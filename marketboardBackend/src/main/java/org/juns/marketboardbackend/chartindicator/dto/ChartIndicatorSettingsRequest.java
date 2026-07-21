package org.juns.marketboardbackend.chartindicator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChartIndicatorSettingsRequest(@NotNull @Valid @Size(max = 5) List<SmaOverlayConfig> smaOverlays) {
}
