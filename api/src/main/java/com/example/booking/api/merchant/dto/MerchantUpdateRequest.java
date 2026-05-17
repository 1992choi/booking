package com.example.booking.api.merchant.dto;

import com.example.booking.api.merchant.domain.MerchantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MerchantUpdateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotNull MerchantType type
) {}
