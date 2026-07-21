package org.juns.marketboardbackend.collector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

/**
 * Server-to-server client for the Python collector's internal FastAPI (no auth/CORS —
 * both processes are trusted, same-host). Calls are best-effort: the collector may be
 * down in dev, so failures are logged and swallowed rather than failing the caller's request.
 */
@Component
public class CollectorClient {

    private static final Logger log = LoggerFactory.getLogger(CollectorClient.class);

    private final RestClient restClient;
    // uvicorn doesn't support the HTTP/2 cleartext (h2c) upgrade the JDK client attempts by
    // default, which it responds to with "Unsupported upgrade request" and mangles the
    // request framing; force HTTP/1.1 explicitly.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public CollectorClient(ObjectMapper objectMapper, @Value("${collector.base-url}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        // No timeout by default means a hung collector call (e.g. yfinance itself hanging/rate
        // limited upstream) blocks the caller's request thread indefinitely -- observed exactly
        // this with /symbol-profile. A read timeout turns that into a fast, catchable
        // RestClientException instead, so callers fall back (stale cache, empty list, etc.)
        // rather than hanging.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public Optional<CollectorHealth> getHealth() {
        try {
            return Optional.ofNullable(restClient.get().uri("/health").retrieve().body(CollectorHealth.class));
        } catch (RestClientException ex) {
            log.warn("Collector health check failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public List<NewsItem> getGeneralNews() {
        return getNews("/news");
    }

    public List<NewsItem> getCompanyNews(String ticker) {
        return getNews("/news/{ticker}", ticker);
    }

    private List<NewsItem> getNews(String uri, Object... uriVariables) {
        try {
            NewsItem[] items = restClient.get().uri(uri, uriVariables).retrieve().body(NewsItem[].class);
            return items != null ? List.of(items) : List.of();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch news from collector ({}): {}", uri, ex.getMessage());
            return List.of();
        }
    }

    public List<MarketIndexInfo> getMarketIndices() {
        try {
            MarketIndexInfo[] items = restClient.get().uri("/market-indices").retrieve().body(MarketIndexInfo[].class);
            return items != null ? List.of(items) : List.of();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch market indices from collector: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<MarketIndexCandle> getMarketIndexHistory(String slug) {
        try {
            MarketIndexCandle[] items =
                    restClient.get().uri("/market-indices/{slug}/history", slug).retrieve().body(MarketIndexCandle[].class);
            return items != null ? List.of(items) : List.of();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch market index history from collector ({}): {}", slug, ex.getMessage());
            return List.of();
        }
    }

    public Optional<FinancialsResponse> getFinancials(String ticker) {
        try {
            return Optional.ofNullable(restClient.get().uri("/financials/{ticker}", ticker).retrieve().body(FinancialsResponse.class));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch financials from collector ({}): {}", ticker, ex.getMessage());
            return Optional.empty();
        }
    }

    // Deliberately bypasses RestClient, same reasoning as syncSubscriptions below: a body-carrying
    // POST through RestClient's JdkClientHttpRequestFactory silently never reaches the collector
    // (confirmed via collector's access log showing no corresponding request) and just eats the
    // full read timeout -- same h2c-upgrade-attempt problem, not fixed by forcing HTTP_1_1 on the
    // shared HttpClient the way it fixed GETs. A dedicated 30s request timeout (rather than
    // reusing the 10s RestClient default) gives a bit more headroom for the DB read + pandas work,
    // even though Phase 1's buy & hold math is normally well under a second.
    public Optional<BacktestEngineResult> runBacktest(BacktestEngineRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/backtest/run"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Backtest run failed via collector: HTTP {} {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(response.body(), BacktestEngineResult.class));
        } catch (IOException ex) {
            log.warn("Backtest run failed via collector (collector may be offline): {}", ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while running backtest via collector: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    // Bodyless POST -- deliberately raw HttpClient anyway (not RestClient) for consistency with
    // runBacktest/syncSubscriptions and to avoid re-litigating whether a bodyless POST is
    // actually safe through RestClient's request factory.
    public boolean backfillTicker(String ticker, String period) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/backfill/" + ticker + "?period=" + period))
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                log.warn("Backfill failed for {}: HTTP {}", ticker, response.statusCode());
                return false;
            }
            return true;
        } catch (IOException ex) {
            log.warn("Backfill failed for {} (collector may be offline): {}", ticker, ex.getMessage());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while backfilling {}: {}", ticker, ex.getMessage());
            return false;
        }
    }

    public Optional<FearGreedResponse> getFearGreed() {
        try {
            return Optional.ofNullable(restClient.get().uri("/sentiment/fear-greed").retrieve().body(FearGreedResponse.class));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch fear & greed index from collector: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<PutCallRatioResponse> getPutCallRatio(String ticker) {
        try {
            return Optional.ofNullable(
                    restClient.get().uri("/sentiment/put-call-ratio?ticker={ticker}", ticker).retrieve().body(PutCallRatioResponse.class));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch put/call ratio from collector ({}): {}", ticker, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<SymbolProfileResponse> getSymbolProfile(String ticker) {
        try {
            return Optional.ofNullable(
                    restClient.get().uri("/symbol-profile/{ticker}", ticker).retrieve().body(SymbolProfileResponse.class));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch symbol profile from collector ({}): {}", ticker, ex.getMessage());
            return Optional.empty();
        }
    }

    // Deliberately bypasses RestClient: its default request factory shares the same JDK HttpClient
    // h2c-upgrade-attempt problem noted above, but the body-carrying PUT is what actually surfaces
    // it as request corruption (uvicorn responds 422 with a null body, then logs "Invalid HTTP
    // request received" for the next request on the same connection). GET has no body to mangle,
    // so getHealth() above is unaffected and can keep using RestClient's defaults.
    public void syncSubscriptions(List<String> tickers) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("symbols", tickers));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/subscriptions"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                log.warn("Failed to sync collector subscriptions: HTTP {}", response.statusCode());
            }
        } catch (IOException ex) {
            log.warn("Failed to sync collector subscriptions (collector may be offline): {}", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while syncing collector subscriptions: {}", ex.getMessage());
        }
    }
}
