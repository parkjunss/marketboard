package org.juns.marketboardbackend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.juns.marketboardbackend.auth.dto.LoginRequest;
import org.juns.marketboardbackend.auth.dto.SignupRequest;
import org.juns.marketboardbackend.auth.dto.TokenResponse;
import org.juns.marketboardbackend.common.exception.AccountSuspendedException;
import org.juns.marketboardbackend.common.exception.DuplicateEmailException;
import org.juns.marketboardbackend.common.exception.InvalidCredentialsException;
import org.juns.marketboardbackend.common.exception.InvalidTokenException;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.juns.marketboardbackend.security.TokenType;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .username(request.username())
                .role(Role.USER)
                .build();
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new AccountSuspendedException();
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
        if (!TokenType.REFRESH.name().equals(claims.get("type", String.class))) {
            throw new InvalidTokenException();
        }

        Long userId = Long.valueOf(claims.getSubject());
        if (!refreshTokenService.matches(userId, refreshToken)) {
            throw new InvalidTokenException();
        }

        User user = userRepository.findById(userId).orElseThrow(InvalidTokenException::new);
        if (!user.isActive()) {
            throw new AccountSuspendedException();
        }
        return issueTokens(user);
    }

    public void logout(Long userId) {
        refreshTokenService.revoke(userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRole());
        refreshTokenService.store(user.getId(), refreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }
}
