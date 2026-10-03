package org.juns.marketboardbackend.user;

import org.juns.marketboardbackend.common.exception.InvalidCredentialsException;
import org.juns.marketboardbackend.auth.RefreshTokenService;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.user.dto.PasswordChangeRequest;
import org.juns.marketboardbackend.user.dto.ProfileResponse;
import org.juns.marketboardbackend.user.dto.ProfileUpdateRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;

    public ProfileService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return ProfileResponse.from(find(userId));
    }

    @Transactional
    public ProfileResponse update(Long userId, ProfileUpdateRequest request) {
        User user = find(userId);
        user.updateProfile(request.username().trim(), request.dailyReportEnabled(),
                request.priceAlertEnabled(), request.importantInfoEnabled());
        return ProfileResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = find(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revoke(userId);
    }

    private User find(Long userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
