package org.juns.marketboardbackend.chartindicator.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SmaOverlayConfig(@Min(2) @Max(500) int period) {
}
