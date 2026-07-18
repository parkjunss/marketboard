package org.juns.marketboardbackend.symbol.dto;

import jakarta.validation.constraints.NotBlank;

public record SymbolCreateRequest(
        @NotBlank String ticker, @NotBlank String name, @NotBlank String exchange, int priority) {
}
