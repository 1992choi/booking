package com.example.booking.api.internal.dto;

import com.example.booking.api.resource.domain.AvailableTime;

import java.time.LocalDateTime;

public record AvailableTimeSnapshot(
        Long id,
        Long resourceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {

    public static AvailableTimeSnapshot from(AvailableTime availableTime) {
        return new AvailableTimeSnapshot(
                availableTime.getId(),
                availableTime.getResourceId(),
                availableTime.getStartTime(),
                availableTime.getEndTime(),
                availableTime.getStatus().name()
        );
    }
}