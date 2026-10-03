package org.juns.marketboardbackend.user;

import jakarta.validation.Valid;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.juns.marketboardbackend.user.dto.PasswordChangeRequest;
import org.juns.marketboardbackend.user.dto.ProfileResponse;
import org.juns.marketboardbackend.user.dto.ProfileUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(user.id());
    }

    @PatchMapping
    public ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody ProfileUpdateRequest request) {
        return service.update(user.id(), request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(user.id(), request);
        return ResponseEntity.noContent().build();
    }
}
