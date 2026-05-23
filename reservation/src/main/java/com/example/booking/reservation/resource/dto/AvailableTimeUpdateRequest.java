package com.example.booking.reservation.resource.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AvailableTimeUpdateRequest(

        @NotNull(message = "시작 시간은 필수입니다.")
        LocalDateTime startTime,

        @NotNull(message = "종료 시간은 필수입니다.")
        LocalDateTime endTime
) {}
