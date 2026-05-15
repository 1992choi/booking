package com.example.booking.reservation.client;

import java.time.LocalDateTime;

public record AvailableTimeSnapshot(
        Long id,
        Long resourceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {
}