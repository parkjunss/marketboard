package org.juns.marketboardbackend.dashboard.dto;

import java.util.List;

public record DashboardConfigResponse(String layoutKey, List<PanelConfigDto> panels) {
}
