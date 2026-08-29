package org.juns.marketboardbackend.collector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.juns.marketboardbackend.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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

    // Not @Cacheable here -- only NewsService.refresh() calls this now (on a schedule, writing
    // the result to MySQL), and CacheConfig.NEWS_GENERAL caches its getGeneralNews() DB read
    // instead. See NewsService for why.
    public List<NewsItem> getGeneralNews() {
        return getNews("/news");
    }

    @Cacheable(value = CacheConfig.NEWS_COMPANY, key = "#ticker", unless = "#result.isEmpty()")
    public List<NewsItem> getCompanyNews(String ticker) {
        return getNews("/news/{ticker}", ticker);
    }

    private List<NewsItem> getNews(String uri, Object... uriVariables) {
        try {
            NewsItem[] items = restClient.get().uri(uri, uriVariables).retrieve().body(NewsItem[].class);
            return items != null ? new ArrayList<>(List.of(items)) : List.of();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch news from collector ({}): {}", uri, ex.getMessage());
            return List.of();
        }
    }

    // Cached results must be a plain ArrayList, not List.of()'s immutable impl -- the latter is a
    // final class in java.util, which Redis's default-typing serializer treats as a "well-known"
    // type and skips tagging with type info, so a cache hit comes back untyped (and, read back as
    // the root Object.class RedisCache always requests, throws deserializing). See CacheConfig.
    @Cacheable(value = CacheConfig.MARKET_INDICES, unless = "#result.isEmpty()")
    public List<MarketIndexInfo> getMarketIndices() {
        try {
            MarketIndexInfo[] items = restClient.get().uri("/market-indices").retrieve().body(MarketIndexInfo[].class);
            return items != null ? new ArrayList<>(List.of(items)) : List.of();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch market indices from collector: {}", ex.getMessage());
            return List.of();
        }
    }

    // Not @Cacheable here -- only SectorPerformanceService.refresh() calls this now (on a
    // schedule, writing the result to MySQL), and CacheConfig.SECTOR_PERFORMANCE caches its
    // getLatest() DB read instead. See SectorPerformanceService for why.
    public List<SectorPerformance> getSectorPerformance() {
        try {
            SectorPerformance[] items = restClient.get().uri("/market-indices/sectors/performance").retrieve().body(SectorPerformance[].class);
            return items != null ? new ArrayList<>(List.of(items)) : List.of();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch sector performance from collector: {}", ex.getMessage());
            return List.of();
        }
    }

    // Not @Cacheable here -- only MarketIndexHistoryService.refresh() calls this now (on a
    // schedule, writing the result to MySQL), and CacheConfig.MARKET_INDEX_HISTORY caches its
    // getHistory() DB read instead. See MarketIndexHistoryService for why.
    public List<MarketIndexCandle> getMarketIndexHistory(String slug) {
        try {
            MarketIndexCandle[] items =
                    restClient.get().uri("/market-indices/{slug}/history", slug).retrieve().body(MarketIndexCandle[].class);
            return items != null ? new ArrayList<>(List.of(items)) : List.of();
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

    // Bodyless GET -- deliberately raw HttpClient anyway (not RestClient's shared 10s default) for
    // a generous timeout: the collector loads the whole S&P 500 universe from price_history, ranks
    // it, then fetches live fundamentals + news sentiment for an oversampled shortlist pool (up to
    // 2x topN, to backfill candidates a market-cap/revenue filter knocks out -- see
    // app/screener.py's ENRICHMENT_POOL_MULTIPLIER) (observed ~15-20s end-to-end for topN=10 with
    // no fundamental filters in dev). 90s gives real headroom above the oversampled worst case.
    public Optional<MomentumScreenerResult> getMomentumScreener(MomentumScreenerRequest request) {
        StringBuilder query = new StringBuilder("?topN=").append(request.topN());
        appendIfPresent(query, "momentumWindowDays", request.momentumWindowDays());
        appendIfPresent(query, "trendMaWindow", request.trendMaWindow());
        appendIfPresent(query, "correlationThreshold", request.correlationThreshold());
        appendIfPresent(query, "minMomentumPct", request.minMomentumPct());
        appendIfPresent(query, "maxRsi", request.maxRsi());
        appendIfPresent(query, "minMarketCap", request.minMarketCap());
        appendIfPresent(query, "minRevenue", request.minRevenue());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/screener/momentum" + query))
                    .timeout(Duration.ofSeconds(90))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Momentum screener failed via collector: HTTP {} {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(response.body(), MomentumScreenerResult.class));
        } catch (IOException ex) {
            log.warn("Momentum screener failed via collector (collector may be offline): {}", ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while running momentum screener via collector: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    // Bodyless GET -- deliberately raw HttpClient anyway (not RestClient's shared 10s default): a
    // 2-ticker (target + SPY) price_history read plus a vectorized Monte Carlo simulation, both
    // fast, but this dev machine's localhost DB connect alone has been observed taking ~10s (see
    // screener.py's ENRICHMENT_POOL_MULTIPLIER comment for the same finding) -- 30s gives headroom.
    public Optional<StockAnalysisResult> getStockAnalysis(
            String ticker, Integer lookbackDays, Integer monteCarloHorizonDays, Integer monteCarloPaths) {
        List<String> params = new ArrayList<>();
        if (lookbackDays != null) params.add("lookbackDays=" + lookbackDays);
        if (monteCarloHorizonDays != null) params.add("monteCarloHorizonDays=" + monteCarloHorizonDays);
        if (monteCarloPaths != null) params.add("monteCarloPaths=" + monteCarloPaths);
        String query = params.isEmpty() ? "" : "?" + String.join("&", params);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/analysis/" + ticker + query))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Stock analysis failed via collector ({}): HTTP {} {}", ticker, response.statusCode(), response.body());
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(response.body(), StockAnalysisResult.class));
        } catch (IOException ex) {
            log.warn("Stock analysis failed via collector ({}, collector may be offline): {}", ticker, ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while running stock analysis via collector ({}): {}", ticker, ex.getMessage());
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

    // Optional<T> return values are unwrapped by the caching abstraction before #result is bound
    // for unless/condition SpEL -- #result is a bare FearGreedResponse (or null), not an Optional,
    // so isEmpty() would fail with a SpelEvaluationException (as isEmpty() did before this fix).
    @Cacheable(value = CacheConfig.FEAR_GREED, unless = "#result == null")
    public Optional<FearGreedResponse> getFearGreed() {
        try {
            return Optional.ofNullable(restClient.get().uri("/sentiment/fear-greed").retrieve().body(FearGreedResponse.class));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch fear & greed index from collector: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    // Not @Cacheable here -- only PutCallRatioService.refresh() calls this now (on a schedule,
    // writing the result to MySQL), and CacheConfig.PUT_CALL_RATIO caches its getLatest() DB read
    // instead. See PutCallRatioService for why.
    public Optional<PutCallRatioResponse> getPutCallRatio(String ticker) {
        try {
            return Optional.ofNullable(
                    restClient.get().uri("/sentiment/put-call-ratio?ticker={ticker}", ticker).retrieve().body(PutCallRatioResponse.class));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch put/call ratio from collector ({}): {}", ticker, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<OptionsLevelsResponse> getOptionsLevels(String ticker) {
        try {
            return Optional.ofNullable(
                    restClient.get().uri("/sentiment/options-levels/{ticker}", ticker).retrieve().body(OptionsLevelsResponse.class));
        } catch (RestClientException ex) {
            log.warn("Failed to fetch options levels from collector ({}): {}", ticker, ex.getMessage());
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

    private static void appendIfPresent(StringBuilder query, String param, Object value) {
        if (value != null) {
            query.append('&').append(param).append('=').append(value);
        }
    }
}
