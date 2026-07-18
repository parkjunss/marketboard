package org.juns.marketboardbackend.admin;

import org.juns.marketboardbackend.admin.dto.CollectorStatusResponse;
import org.juns.marketboardbackend.admin.dto.SystemStatusResponse;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final CollectorClient collectorClient;
    private final SimpUserRegistry simpUserRegistry;

    public AdminDashboardController(CollectorClient collectorClient, SimpUserRegistry simpUserRegistry) {
        this.collectorClient = collectorClient;
        this.simpUserRegistry = simpUserRegistry;
    }

    @GetMapping("/collector/status")
    public CollectorStatusResponse getCollectorStatus() {
        return collectorClient.getHealth()
                .map(CollectorStatusResponse::of)
                .orElseGet(CollectorStatusResponse::unreachable);
    }

    @GetMapping("/system/status")
    public SystemStatusResponse getSystemStatus() {
        int activeSessions = simpUserRegistry.getUsers().stream()
                .mapToInt(user -> user.getSessions().size())
                .sum();
        return new SystemStatusResponse(simpUserRegistry.getUserCount(), activeSessions);
    }
}
