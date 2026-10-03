package org.juns.marketboardbackend.notification;

import org.juns.marketboardbackend.notification.dto.NotificationListResponse;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationListResponse getAll(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.getAll(user.id());
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        service.markRead(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        service.markAllRead(user.id());
        return ResponseEntity.noContent().build();
    }
}
