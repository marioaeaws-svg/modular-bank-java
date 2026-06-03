package com.modularbank.modules.auth.application;

import com.modularbank.modules.auth.application.dto.AuthResponse;
import com.modularbank.modules.auth.application.dto.LoginRequest;
import com.modularbank.modules.auth.application.dto.RegisterRequest;
import com.modularbank.modules.auth.domain.RefreshToken;
import com.modularbank.modules.auth.domain.User;
import com.modularbank.modules.auth.infrastructure.RefreshTokenRepository;
import com.modularbank.modules.auth.infrastructure.UserRepository;
import com.modularbank.shared.infrastructure.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-expiration-days}")
    private int refreshExpirationDays;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = User.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .name(request.name())
            .build();
        userRepository.save(user);
        return buildAuthResponse(user.getId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return buildAuthResponse(user.getId());
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(rawToken)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        refreshTokenRepository.delete(refreshToken);
        return buildAuthResponse(refreshToken.getUserId());
    }

    private AuthResponse buildAuthResponse(java.util.UUID userId) {
        String accessToken = jwtUtil.generateAccessToken(userId);
        String rawRefreshToken = generateSecureToken();
        RefreshToken refreshToken = RefreshToken.builder()
            .userId(userId)
            .token(rawRefreshToken)
            .expiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS))
            .build();
        refreshTokenRepository.save(refreshToken);
        return new AuthResponse(accessToken, rawRefreshToken);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
