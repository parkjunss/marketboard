package org.juns.marketboardbackend.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.pricehistory.PriceHistory;
import org.juns.marketboardbackend.pricehistory.PriceHistoryRepository;
import org.juns.marketboardbackend.quote.dto.CandleResponse;
import org.juns.marketboardbackend.quote.dto.QuoteResponse;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private static final String QUOTE_KEY_PREFIX = "quote:";

    private final StringRedisTemplate redisTemplate;
    private final SymbolRepository symbolRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public QuoteService(
            StringRedisTemplate redisTemplate,
            SymbolRepository symbolRepository,
            PriceHistoryRepository priceHistoryRepository) {
        this.redisTemplate = redisTemplate;
        this.symbolRepository = symbolRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    public List<QuoteResponse> getActiveQuotes() {
        return symbolRepository.findByActiveTrueOrderByPriorityAsc().stream()
                .map(symbol -> readQuote(symbol.getTicker())
                        .map(quote -> quote.withName(symbol.getName()))
                        .orElseGet(() -> QuoteResponse.empty(symbol.getTicker(), symbol.getName())))
                .toList();
    }

    public QuoteResponse getQuote(String ticker) {
        String normalized = ticker.toUpperCase();
        QuoteResponse quote = readQuote(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("No live quote for " + normalized));
        return symbolRepository.findByTickerIgnoreCase(normalized)
                .map(symbol -> quote.withName(symbol.getName()))
                .orElse(quote);
    }

    public List<CandleResponse> getHistory(String ticker, String timeframe, int limit) {
        Symbol symbol = symbolRepository.findByTickerIgnoreCase(ticker)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown symbol " + ticker.toUpperCase()));
        return priceHistoryRepository
                .findBySymbol_IdAndTimeframeOrderByTsDesc(symbol.getId(), timeframe, PageRequest.of(0, limit))
                .stream()
                .sorted(Comparator.comparing(candle -> candle.getTs()))
                .map(CandleResponse::from)
                .toList();
    }

    /** Single and bulk callers use the same source selection and metadata policy. */
    public Optional<ResolvedPrice> resolvePrice(String ticker) {
        return Optional.ofNullable(resolvePrices(List.of(ticker)).get(ticker.toUpperCase(java.util.Locale.ROOT)));
    }

    public Map<String, ResolvedPrice> resolvePrices(Collection<String> tickers) {
        Set<String> normalized = tickers.stream().map(t -> t.toUpperCase(java.util.Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ResolvedPrice> resolved = new HashMap<>();
        List<String> needsHistory = new ArrayList<>();
        Instant now = Instant.now();
        for (String ticker : normalized) {
            Optional<ResolvedPrice> cached = readResolvedQuote(ticker, now);
            cached.ifPresent(price -> resolved.put(ticker, price));
            if (cached.isEmpty() || !cached.get().isLive()) needsHistory.add(ticker);
        }
        if (needsHistory.isEmpty()) return resolved;
        Map<Long, String> tickerById = symbolRepository.findByTickerIn(needsHistory).stream()
                .collect(Collectors.toMap(Symbol::getId, Symbol::getTicker));
        if (tickerById.isEmpty()) return resolved;
        for (PriceHistory candle : priceHistoryRepository.findLatestDailyBySymbolIds(tickerById.keySet())) {
            if (candle.getClose() == null || candle.getClose().signum() <= 0 || candle.getTs().isAfter(now)) continue;
            String ticker = tickerById.get(candle.getSymbol().getId()).toUpperCase(java.util.Locale.ROOT);
            ResolvedPrice cached = resolved.get(ticker);
            if (cached == null || cached.asOf() == null || candle.getTs().isAfter(cached.asOf())) {
                // Daily ts is the bar's session timestamp, NOT its closing observation time.
                // Legacy history has no provider/adjustment metadata or exchange calendar check.
                resolved.put(ticker, new ResolvedPrice(candle.getClose(), "CLOSE", "UNKNOWN", null, null,
                        candle.getTs().atZone(java.time.ZoneId.of("America/New_York")).toLocalDate(), "UNVERIFIED"));
            }
        }
        return resolved;
    }

    private Optional<ResolvedPrice> readResolvedQuote(String ticker, Instant now) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(QUOTE_KEY_PREFIX + ticker);
        if (fields.isEmpty()) return Optional.empty();
        try {
            BigDecimal price = new BigDecimal(String.valueOf(fields.get("price")));
            if (price.signum() <= 0) return Optional.empty();
            String provider = String.valueOf(fields.getOrDefault("source", "UNKNOWN"));
            if (!Set.of("FINNHUB", "YFINANCE").contains(provider)) provider = "UNKNOWN";
            Instant fetchedAt = parseInstant(fields.get("fetchedAt"));
            // Legacy and REST values may carry a fetch time in ts; only explicit Finnhub is trusted.
            Instant observedAt = "FINNHUB".equals(provider) ? parseInstant(fields.get("ts")) : null;
            if (observedAt != null && observedAt.isAfter(now)) observedAt = null;
            String status = observedAt == null ? "UNVERIFIED"
                    : observedAt.isBefore(now.minusSeconds(120)) ? "STALE" : "RECENT";
            return Optional.of(new ResolvedPrice(price, "FINNHUB".equals(provider) ? "LIVE" : "CACHED",
                    provider, observedAt, fetchedAt, null, status));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Instant parseInstant(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Instant.parse(value.toString());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private Optional<QuoteResponse> readQuote(String ticker) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(QUOTE_KEY_PREFIX + ticker);
        if (fields.isEmpty()) return Optional.empty();
        try {
            BigDecimal price = new BigDecimal(String.valueOf(fields.get("price")));
            if (price.signum() <= 0) return Optional.empty();
            return Optional.of(new QuoteResponse(ticker, null, price,
                    new BigDecimal(String.valueOf(fields.getOrDefault("volume", "0"))),
                    "FINNHUB".equals(fields.get("source")) ? parseInstant(fields.get("ts")) : null));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
