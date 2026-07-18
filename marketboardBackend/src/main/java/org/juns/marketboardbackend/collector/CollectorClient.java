package org.juns.marketboardbackend.collector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public CollectorClient(ObjectMapper objectMapper, @Value("${collector.base-url}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
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
