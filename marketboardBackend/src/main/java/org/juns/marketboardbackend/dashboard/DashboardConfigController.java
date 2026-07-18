package org.juns.marketboardbackend.dashboard;

import jakarta.validation.Valid;
import org.juns.marketboardbackend.dashboard.dto.DashboardConfigRequest;
import org.juns.marketboardbackend.dashboard.dto.DashboardConfigResponse;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardConfigController {

    private final DashboardConfigService dashboardConfigService;

    public DashboardConfigController(DashboardConfigService dashboardConfigService) {
        this.dashboardConfigService = dashboardConfigService;
    }

    @GetMapping
    public DashboardConfigResponse get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return dashboardConfigService.get(principal.id());
    }

    @PutMapping
    public DashboardConfigResponse save(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody DashboardConfigRequest request) {
        return dashboardConfigService.save(principal.id(), request);
    }
}
