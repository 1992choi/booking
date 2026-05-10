package com.example.booking.reservation.dto;

import com.example.booking.reservation.domain.Reservation;
import com.example.booking.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        ReservationStatus status,
        String resourceName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int headCount,
        Long amount
) {

    public static ReservationResponse from(Reservation reservation, String resourceName) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getStatus(),
                resourceName,
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getHeadCount(),
                reservation.getAmount()
        );
    }
}