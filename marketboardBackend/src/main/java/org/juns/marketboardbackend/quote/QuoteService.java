package org.juns.marketboardbackend.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /**
     * Best-effort current price for tickers that may not be in the real-time WS set (e.g. an
     * arbitrary portfolio position) — falls back to the latest daily close when no live tick exists.
     */
    public Optional<ResolvedPrice> resolvePrice(String ticker) {
        Optional<QuoteResponse> live = readQuote(ticker);
        if (live.isPresent() && live.get().price() != null) {
            return Optional.of(new ResolvedPrice(live.get().price(), true));
        }
        return symbolRepository
                .findByTickerIgnoreCase(ticker)
                .flatMap(symbol -> priceHistoryRepository.findFirstBySymbol_IdAndTimeframeOrderByTsDesc(symbol.getId(), "1d"))
                .map(candle -> new ResolvedPrice(candle.getClose(), false));
    }

    /**
     * Bulk version of {@link #resolvePrice(String)} for a whole portfolio's positions at once --
     * avoids the DB fallback's symbol + price-history lookups running once per position. The
     * per-ticker Redis read stays as-is (it's an in-memory HGETALL, not a relational query, and a
     * portfolio's position count is small enough that pipelining it wouldn't be worth the added
     * complexity); only the DB fallback path for tickers with no live tick is batched.
     *
     * @return a map containing only the tickers a price could be resolved for -- missing tickers
     *     mean the same "no price available" case {@link #resolvePrice(String)} signals with an
     *     empty Optional.
     */
    public Map<String, ResolvedPrice> resolvePrices(Collection<String> tickers) {
        Set<String> normalizedTickers = tickers.stream().map(String::toUpperCase).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, ResolvedPrice> resolved = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String ticker : normalizedTickers) {
            Optional<QuoteResponse> live = readQuote(ticker);
            if (live.isPresent() && live.get().price() != null) {
                resolved.put(ticker, new ResolvedPrice(live.get().price(), true));
            } else {
                missing.add(ticker);
            }
        }
        if (missing.isEmpty()) {
            return resolved;
        }

        List<Symbol> symbols = symbolRepository.findByTickerIn(missing);
        Map<Long, String> tickerBySymbolId = symbols.stream().collect(Collectors.toMap(Symbol::getId, Symbol::getTicker));
        if (tickerBySymbolId.isEmpty()) {
            return resolved;
        }

        Map<Long, PriceHistory> latestCandleBySymbolId = priceHistoryRepository
                .findBySymbol_IdInAndTimeframeAndTsGreaterThanEqual(
                        tickerBySymbolId.keySet(), "1d", Instant.now().minus(10, ChronoUnit.DAYS))
                .stream()
                .collect(Collectors.toMap(
                        candle -> candle.getSymbol().getId(),
                        candle -> candle,
                        (a, b) -> a.getTs().isAfter(b.getTs()) ? a : b));

        latestCandleBySymbolId.forEach((symbolId, candle) -> {
            String ticker = tickerBySymbolId.get(symbolId);
            resolved.put(ticker, new ResolvedPrice(candle.getClose(), false));
        });
        return resolved;
    }

    private Optional<QuoteResponse> readQuote(String ticker) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(QUOTE_KEY_PREFIX + ticker);
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QuoteResponse(
                ticker,
                null,
                new BigDecimal((String) fields.get("price")),
                new BigDecimal((String) fields.get("volume")),
                Instant.parse((String) fields.get("ts"))));
    }
}
