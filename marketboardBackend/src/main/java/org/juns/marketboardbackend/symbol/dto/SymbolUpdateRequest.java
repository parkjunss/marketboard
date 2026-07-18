package org.juns.marketboardbackend.symbol.dto;

import jakarta.validation.constraints.NotBlank;

public record SymbolUpdateRequest(
        @NotBlank String name, @NotBlank String exchange, boolean active, int priority) {
}
