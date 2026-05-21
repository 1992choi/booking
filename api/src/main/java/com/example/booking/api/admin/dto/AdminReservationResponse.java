package com.example.booking.api.admin.dto;

import java.time.LocalDateTime;

public record AdminReservationResponse(
        Long id,
        String status,
        String resourceName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int headCount,
        Long amount,
        Long userId,
        String userName
) {}