package org.juns.marketboardbackend.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Deliberately opaque on the backend: `type` (and which of ticker/timeframe/indicator apply) is a
 * frontend concern. The backend just persists and returns this blob per user, so new panel types
 * don't require a backend change.
 */
public record PanelConfigDto(
        @NotNull Integer slot, @NotBlank String type, String ticker, String timeframe, String indicator) {
}
