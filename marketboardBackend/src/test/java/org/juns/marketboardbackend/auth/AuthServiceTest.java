package org.juns.marketboardbackend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.juns.marketboardbackend.auth.dto.LoginRequest;
import org.juns.marketboardbackend.auth.dto.SignupRequest;
import org.juns.marketboardbackend.auth.dto.TokenResponse;
import org.juns.marketboardbackend.common.exception.AccountSuspendedException;
import org.juns.marketboardbackend.common.exception.DuplicateEmailException;
import org.juns.marketboardbackend.common.exception.InvalidCredentialsException;
import org.juns.marketboardbackend.common.exception.InvalidTokenException;
import org.juns.marketboardbackend.security.JwtProperties;
import org.juns.marketboardbackend.security.JwtTokenProvider;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String JWT_SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-32-bytes-long";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(new JwtProperties(JWT_SECRET, 900_000L, 604_800_000L));
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, refreshTokenService);
    }

    private User activeUser(Long id) {
        User user = User.builder()
                .email("user@example.com")
                .passwordHash("encoded-hash")
                .username("tester")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void signup_savesUserWithEncodedPasswordAndUserRole() {
        SignupRequest request = new SignupRequest("new@example.com", "password123", "newbie");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-pw");

        authService.signup(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getUsername()).isEqualTo("newbie");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-pw");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void signup_duplicateEmail_throwsAndDoesNotSave() {
        SignupRequest request = new SignupRequest("dup@example.com", "password123", "dup");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_returnsTokensAndStoresRefreshToken() {
        User user = activeUser(1L);
        LoginRequest request = new LoginRequest(user.getEmail(), "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);

        TokenResponse response = authService.login(request);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenService).store(eq(1L), eq(response.refreshToken()));
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("ghost@example.com", "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).store(any(), anyString());
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User user = activeUser(1L);
        LoginRequest request = new LoginRequest(user.getEmail(), "wrong-password");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).store(any(), anyString());
    }

    @Test
    void login_suspendedAccount_throwsAccountSuspended() {
        User user = activeUser(1L);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        LoginRequest request = new LoginRequest(user.getEmail(), "password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountSuspendedException.class);
        verify(refreshTokenService, never()).store(any(), anyString());
    }

    @Test
    void refresh_validToken_returnsNewTokenPair() {
        User user = activeUser(1L);
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L, user.getEmail(), user.getRole());
        when(refreshTokenService.matches(1L, refreshToken)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        TokenResponse response = authService.refresh(refreshToken);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenService).store(eq(1L), eq(response.refreshToken()));
    }

    @Test
    void refresh_malformedToken_throwsInvalidToken() {
        assertThatThrownBy(() -> authService.refresh("not-a-valid-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_accessTokenUsedAsRefresh_throwsInvalidToken() {
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "user@example.com", Role.USER);

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_notMatchingStoredToken_throwsInvalidToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L, "user@example.com", Role.USER);
        when(refreshTokenService.matches(1L, refreshToken)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void refresh_suspendedUser_throwsAccountSuspended() {
        User user = activeUser(1L);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L, user.getEmail(), user.getRole());
        when(refreshTokenService.matches(1L, refreshToken)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(AccountSuspendedException.class);
    }

    @Test
    void logout_revokesRefreshToken() {
        authService.logout(1L);

        verify(refreshTokenService, times(1)).revoke(1L);
    }
}
