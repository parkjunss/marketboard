package org.juns.marketboardbackend.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteResponse(String symbol, String name, BigDecimal price, BigDecimal volume, Instant ts) {

    public static QuoteResponse empty(String symbol, String name) {
        return new QuoteResponse(symbol, name, null, null, null);
    }

    public QuoteResponse withName(String name) {
        return new QuoteResponse(symbol, name, price, volume, ts);
    }
}
