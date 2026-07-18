package org.juns.marketboardbackend.symbol.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SymbolBulkActiveRequest(@NotEmpty List<Long> ids, boolean active) {
}
