package com.example.booking.reservation.resource.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResourceCreateRequest(

        @NotBlank(message = "이름은 필수입니다.")
        String name,

        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
        Long price,

        @NotNull(message = "최대 수용 인원은 필수입니다.")
        @Min(value = 1, message = "최대 수용 인원은 1 이상이어야 합니다.")
        Integer maxCapacity
) {}
