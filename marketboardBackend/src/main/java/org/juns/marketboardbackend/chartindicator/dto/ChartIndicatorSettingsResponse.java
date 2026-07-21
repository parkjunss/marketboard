package org.juns.marketboardbackend.chartindicator.dto;

import java.util.List;

public record ChartIndicatorSettingsResponse(List<SmaOverlayConfig> smaOverlays) {
}
