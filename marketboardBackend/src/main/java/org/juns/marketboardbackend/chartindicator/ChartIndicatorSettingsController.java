package org.juns.marketboardbackend.chartindicator;

import jakarta.validation.Valid;
import org.juns.marketboardbackend.chartindicator.dto.ChartIndicatorSettingsRequest;
import org.juns.marketboardbackend.chartindicator.dto.ChartIndicatorSettingsResponse;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chart-indicator-settings")
public class ChartIndicatorSettingsController {

    private final ChartIndicatorSettingsService chartIndicatorSettingsService;

    public ChartIndicatorSettingsController(ChartIndicatorSettingsService chartIndicatorSettingsService) {
        this.chartIndicatorSettingsService = chartIndicatorSettingsService;
    }

    @GetMapping
    public ChartIndicatorSettingsResponse get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return chartIndicatorSettingsService.get(principal.id());
    }

    @PutMapping
    public ChartIndicatorSettingsResponse save(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody ChartIndicatorSettingsRequest request) {
        return chartIndicatorSettingsService.save(principal.id(), request);
    }
}
