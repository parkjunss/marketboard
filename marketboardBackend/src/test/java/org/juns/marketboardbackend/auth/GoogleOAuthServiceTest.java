package org.juns.marketboardbackend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.juns.marketboardbackend.auth.dto.TokenResponse;
import org.juns.marketboardbackend.common.exception.AccountSuspendedException;
import org.juns.marketboardbackend.common.exception.InvalidTokenException;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.juns.marketboardbackend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {
    @Mock OAuthAccountRepository accounts;
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock OAuthLoginCodeService loginCodes;
    @Mock AuthService authService;
    GoogleOAuthService service;

    @BeforeEach
    void setUp() {
        service = new GoogleOAuthService(accounts, users, passwordEncoder, loginCodes, authService);
    }

    @Test
    void existingEmail_linksGoogleAccountAndReturnsOneTimeCode() {
        User user = user(7L);
        when(accounts.findByProviderAndProviderSubject(OAuthProvider.GOOGLE, "google-sub")).thenReturn(Optional.empty());
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(loginCodes.create(7L)).thenReturn("code");

        assertThat(service.completeLogin(principal())).isEqualTo("code");

        ArgumentCaptor<OAuthAccount> account = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(accounts).save(account.capture());
        assertThat(account.getValue().getUser()).isSameAs(user);
        assertThat(account.getValue().getProviderSubject()).isEqualTo("google-sub");
        verify(users, never()).save(any());
    }

    @Test
    void newEmail_createsUserAndLinksAccount() {
        when(accounts.findByProviderAndProviderSubject(OAuthProvider.GOOGLE, "google-sub")).thenReturn(Optional.empty());
        when(users.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("random-hash");
        when(users.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 8L);
            return saved;
        });
        when(loginCodes.create(8L)).thenReturn("code");

        assertThat(service.completeLogin(principal())).isEqualTo("code");

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(users).save(created.capture());
        assertThat(created.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(created.getValue().getUsername()).isEqualTo("Google User");
        assertThat(created.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void suspendedLinkedUser_isRejected() {
        User user = user(7L);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        when(accounts.findByProviderAndProviderSubject(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(new OAuthAccount(user, OAuthProvider.GOOGLE, "google-sub")));

        assertThatThrownBy(() -> service.completeLogin(principal())).isInstanceOf(AccountSuspendedException.class);
        verify(loginCodes, never()).create(any());
    }

    @Test
    void exchange_consumesCodeAndIssuesJwtForLongUserId() {
        User user = user(42L);
        TokenResponse tokens = new TokenResponse("access", "refresh");
        when(loginCodes.consume("code")).thenReturn(42L);
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(authService.issueTokens(user)).thenReturn(tokens);

        assertThat(service.exchange("code")).isSameAs(tokens);
        verify(authService).issueTokens(user);
    }

    @Test
    void exchange_reusedOrExpiredCode_isRejected() {
        when(loginCodes.consume("code")).thenReturn(null);
        assertThatThrownBy(() -> service.exchange("code")).isInstanceOf(InvalidTokenException.class);
    }

    private OAuth2User principal() {
        return new DefaultOAuth2User(
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-sub", "email", "user@example.com", "email_verified", true,
                        "name", "Google User"), "sub");
    }

    private User user(Long id) {
        User user = User.builder().email("user@example.com").passwordHash("hash")
                .username("user").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
