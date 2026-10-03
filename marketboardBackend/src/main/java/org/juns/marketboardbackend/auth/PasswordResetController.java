package org.juns.marketboardbackend.auth;

import jakarta.validation.Valid;
import org.juns.marketboardbackend.auth.dto.PasswordForgotRequest;
import org.juns.marketboardbackend.auth.dto.PasswordResetRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController {
    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody PasswordForgotRequest request) {
        service.request(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetRequest request) {
        service.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
