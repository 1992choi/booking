package com.example.booking.api.auth;

import com.example.booking.core.auth.AuthPrincipal;

import java.time.Instant;

public record RefreshTokenClaims(AuthPrincipal principal, String jti, Instant expiresAt) {}
