package org.juns.marketboardbackend.dashboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DashboardConfigRequest(
        @NotBlank String layoutKey, @NotNull @Valid List<PanelConfigDto> panels) {
}
