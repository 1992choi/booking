package com.example.booking.api.auth.service;

import com.example.booking.api.auth.JwtIssuer;
import com.example.booking.api.auth.RefreshTokenBlacklist;
import com.example.booking.api.auth.RefreshTokenClaims;
import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.LogoutRequest;
import com.example.booking.api.auth.dto.RefreshRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.error.ApiErrorCode;
import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import com.example.booking.api.user.event.UserCreatedDomainEvent;
import com.example.booking.core.auth.Role;
import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenBlacklist refreshTokenBlacklist;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public User register(SignupRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(ApiErrorCode.EMAIL_DUPLICATED);
        }

        User user = userRepository.save(User.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build());
        eventPublisher.publishEvent(new UserCreatedDomainEvent(user.getId(), user.getName(), user.getEmail(), user.getPhone()));
        log.info("회원가입 userId={}, email={}", user.getId(), user.getEmail());
        return user;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ApiErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ApiErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtIssuer.issue(user.getId(), user.getRole());
        String refreshToken = jwtIssuer.issueRefreshToken(user.getId(), user.getRole());
        log.info("로그인 userId={}", user.getId());
        return TokenResponse.ofLogin(accessToken, refreshToken, jwtIssuer.accessTokenTtlSeconds());
    }

    public TokenResponse refresh(RefreshRequest request) {
        try {
            RefreshTokenClaims claims = jwtIssuer.verifyRefreshToken(request.refreshToken());
            if (refreshTokenBlacklist.isBlacklisted(claims.jti())) {
                log.warn("블랙리스트 토큰으로 갱신 시도 userId={}", claims.principal().userId());
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
            }
            String newAccessToken = jwtIssuer.issue(claims.principal().userId(), claims.principal().role());
            return TokenResponse.ofRefresh(newAccessToken, jwtIssuer.accessTokenTtlSeconds());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    public void logout(LogoutRequest request) {
        try {
            RefreshTokenClaims claims = jwtIssuer.verifyRefreshToken(request.refreshToken());
            refreshTokenBlacklist.add(claims.jti(), claims.expiresAt());
            log.info("로그아웃 userId={}", claims.principal().userId());
        } catch (JwtException | IllegalArgumentException ignored) {
            // 이미 만료되거나 유효하지 않은 토큰 — 무시 (멱등성 보장)
        }
    }
}