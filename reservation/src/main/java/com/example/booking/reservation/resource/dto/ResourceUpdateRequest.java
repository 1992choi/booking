package com.example.booking.reservation.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ResourceUpdateRequest(
        @NotBlank String name,
        String description,
        @NotNull @Positive Long price,
        @NotNull @Positive Integer maxCapacity
) {}
