package com.example.booking.api.admin.dto;

public record AdminCalendarEntry(
        Long reservationId,
        String resourceName,
        String startTime,
        String endTime,
        String status
) {}