package org.juns.marketboardbackend.auth;

import jakarta.validation.Valid;
import org.juns.marketboardbackend.auth.dto.TokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/oauth")
public class GoogleOAuthController {
    private final GoogleOAuthService service;

    public GoogleOAuthController(GoogleOAuthService service) { this.service = service; }

    @PostMapping("/exchange")
    public ResponseEntity<TokenResponse> exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return ResponseEntity.ok(service.exchange(request.code()));
    }
}
