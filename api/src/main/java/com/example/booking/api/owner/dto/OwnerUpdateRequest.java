package com.example.booking.api.owner.dto;

import com.example.booking.api.owner.domain.OwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OwnerUpdateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotNull OwnerType type
) {}