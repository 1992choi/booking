package com.example.booking.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {

    public static TokenResponse ofLogin(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, expiresIn);
    }

    public static TokenResponse ofRefresh(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, null, expiresIn);
    }
}