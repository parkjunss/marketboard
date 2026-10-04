package org.juns.marketboardbackend.auth;

import java.util.UUID;
import org.juns.marketboardbackend.auth.dto.TokenResponse;
import org.juns.marketboardbackend.common.exception.AccountSuspendedException;
import org.juns.marketboardbackend.common.exception.InvalidTokenException;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOAuthService {
    private final OAuthAccountRepository accounts;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OAuthLoginCodeService loginCodes;
    private final AuthService authService;

    public GoogleOAuthService(OAuthAccountRepository accounts, UserRepository users, PasswordEncoder passwordEncoder,
                              OAuthLoginCodeService loginCodes, AuthService authService) {
        this.accounts = accounts;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.loginCodes = loginCodes;
        this.authService = authService;
    }

    @Transactional
    public String completeLogin(OAuth2User principal) {
        String subject = required(principal, "sub");
        String email = required(principal, "email");
        if (!Boolean.TRUE.equals(principal.getAttribute("email_verified"))) throw new InvalidTokenException();

        User user = accounts.findByProviderAndProviderSubject(OAuthProvider.GOOGLE, subject)
                .map(OAuthAccount::getUser)
                .orElseGet(() -> linkOrCreate(subject, email, principal.getAttribute("name")));
        if (!user.isActive()) throw new AccountSuspendedException();
        return loginCodes.create(user.getId());
    }

    @Transactional(readOnly = true)
    public TokenResponse exchange(String code) {
        Long userId = loginCodes.consume(code);
        if (userId == null) throw new InvalidTokenException();
        User user = users.findById(userId).orElseThrow(InvalidTokenException::new);
        if (!user.isActive()) throw new AccountSuspendedException();
        return authService.issueTokens(user);
    }

    private User linkOrCreate(String subject, String email, String name) {
        User user = users.findByEmail(email).orElseGet(() -> users.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .username(name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name)
                .role(Role.USER)
                .build()));
        accounts.save(new OAuthAccount(user, OAuthProvider.GOOGLE, subject));
        return user;
    }

    private String required(OAuth2User principal, String name) {
        String value = principal.getAttribute(name);
        if (value == null || value.isBlank()) throw new InvalidTokenException();
        return value;
    }
}
