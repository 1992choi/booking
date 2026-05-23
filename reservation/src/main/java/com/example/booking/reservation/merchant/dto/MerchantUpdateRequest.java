package com.example.booking.reservation.merchant.dto;

import com.example.booking.reservation.merchant.domain.MerchantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MerchantUpdateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotNull MerchantType type
) {}
