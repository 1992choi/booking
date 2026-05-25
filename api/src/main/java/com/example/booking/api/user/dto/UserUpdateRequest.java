package com.example.booking.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserUpdateRequest(
        @NotBlank String name,
        @NotBlank String phone
) {
}
