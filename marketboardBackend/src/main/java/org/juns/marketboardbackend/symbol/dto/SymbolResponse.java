package org.juns.marketboardbackend.symbol.dto;

import org.juns.marketboardbackend.symbol.Symbol;

public record SymbolResponse(Long id, String ticker, String name, String exchange, boolean active, int priority) {

    public static SymbolResponse from(Symbol symbol) {
        return new SymbolResponse(
                symbol.getId(), symbol.getTicker(), symbol.getName(), symbol.getExchange(), symbol.isActive(), symbol.getPriority());
    }
}
