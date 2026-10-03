package org.juns.marketboardbackend.auth;

import java.time.Duration;
import java.util.UUID;
import org.juns.marketboardbackend.common.exception.InvalidTokenException;
import org.juns.marketboardbackend.mail.MailService;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String PREFIX = "password-reset:";
    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final String frontendUrl;
    private final RefreshTokenService refreshTokens;

    public PasswordResetService(UserRepository users, StringRedisTemplate redis, PasswordEncoder passwordEncoder,
                                MailService mail, RefreshTokenService refreshTokens,
                                @Value("${app.frontend-url:http://localhost:3100}") String frontendUrl) {
        this.users = users;
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
        this.refreshTokens = refreshTokens;
    }

    public void request(String email) {
        users.findByEmail(email.trim().toLowerCase()).filter(User::isActive).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            redis.opsForValue().set(PREFIX + token, String.valueOf(user.getId()), Duration.ofMinutes(30));
            try {
                mail.send(user.getEmail(), "MarketBoard 비밀번호 재설정",
                        frontendUrl + "/reset-password?token=" + token + "\n\n이 링크는 30분 후 만료됩니다.");
            } catch (RuntimeException ex) {
                log.warn("Password reset email delivery failed for user {}", user.getId(), ex);
            }
        });
    }

    @Transactional
    public void reset(String token, String newPassword) {
        String userId = redis.opsForValue().getAndDelete(PREFIX + token);
        if (userId == null) throw new InvalidTokenException();
        User user = users.findById(Long.valueOf(userId)).orElseThrow(InvalidTokenException::new);
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.revoke(user.getId());
    }
}
