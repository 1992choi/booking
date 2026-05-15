package com.example.booking.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateReservationRequest(

        @NotNull(message = "설비 ID는 필수입니다.")
        Long resourceId,

        @NotEmpty(message = "예약 시간 슬롯은 1개 이상이어야 합니다.")
        List<Long> availableTimeIds,

        @Min(value = 1, message = "인원은 1명 이상이어야 합니다.")
        int headCount
) {
}