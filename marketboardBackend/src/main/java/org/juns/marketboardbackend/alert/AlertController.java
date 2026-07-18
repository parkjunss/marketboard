package org.juns.marketboardbackend.alert;

import jakarta.validation.Valid;
import java.util.List;
import org.juns.marketboardbackend.alert.dto.AlertRequest;
import org.juns.marketboardbackend.alert.dto.AlertResponse;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertResponse> getAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        return alertService.getAll(principal.id());
    }

    @PostMapping
    public ResponseEntity<AlertResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody AlertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.create(principal.id(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        alertService.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }
}
