package org.juns.marketboardbackend.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
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
                .findBySymbol_TickerIgnoreCaseAndTimeframeOrderByTsDesc(symbol.getTicker(), timeframe, PageRequest.of(0, limit))
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
        return priceHistoryRepository
                .findFirstBySymbol_TickerIgnoreCaseAndTimeframeOrderByTsDesc(ticker, "1d")
                .map(candle -> new ResolvedPrice(candle.getClose(), false));
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
