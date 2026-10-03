package org.juns.marketboardbackend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.juns.marketboardbackend.common.exception.InvalidCredentialsException;
import org.juns.marketboardbackend.auth.RefreshTokenService;
import org.juns.marketboardbackend.user.dto.PasswordChangeRequest;
import org.juns.marketboardbackend.user.dto.ProfileUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock RefreshTokenService refreshTokens;
    ProfileService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new ProfileService(users, passwords, refreshTokens);
        user = User.builder().email("user@example.com").passwordHash("old").username("before").role(Role.USER).build();
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void updatesVisibleIdAndDailyReportPreference() {
        var result = service.update(1L, new ProfileUpdateRequest(" after ", true, false, true));
        assertThat(result.username()).isEqualTo("after");
        assertThat(result.dailyReportEnabled()).isTrue();
        assertThat(result.priceAlertEnabled()).isFalse();
        assertThat(result.importantInfoEnabled()).isTrue();
    }

    @Test
    void rejectsWrongCurrentPassword() {
        when(passwords.matches("wrong", "old")).thenReturn(false);
        assertThatThrownBy(() -> service.changePassword(1L, new PasswordChangeRequest("wrong", "new-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
