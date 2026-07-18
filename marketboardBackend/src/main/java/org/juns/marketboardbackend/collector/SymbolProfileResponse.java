package org.juns.marketboardbackend.collector;

public record SymbolProfileResponse(
        String ticker, String name, String exchange, String sector, String industry, String currency, Long marketCap) {
}
