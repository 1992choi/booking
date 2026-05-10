package com.example.booking.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateReservationRequest(

        @NotNull(message = "설비 ID는 필수입니다.")
        Long resourceId,

        @NotNull(message = "시작 시간은 필수입니다.")
        LocalDateTime startTime,

        @NotNull(message = "종료 시간은 필수입니다.")
        LocalDateTime endTime,

        @Min(value = 1, message = "인원은 1명 이상이어야 합니다.")
        int headCount
) {
}