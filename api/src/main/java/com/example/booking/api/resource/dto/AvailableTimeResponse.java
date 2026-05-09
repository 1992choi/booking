package com.example.booking.api.resource.dto;

import com.example.booking.api.resource.domain.AvailableTime;
import com.example.booking.api.resource.domain.AvailableTimeStatus;

import java.time.LocalDateTime;

public record AvailableTimeResponse(
        Long id,
        LocalDateTime startTime,
        LocalDateTime endTime,
        AvailableTimeStatus status
) {

    public static AvailableTimeResponse from(AvailableTime availableTime) {
        return new AvailableTimeResponse(
                availableTime.getId(),
                availableTime.getStartTime(),
                availableTime.getEndTime(),
                availableTime.getStatus()
        );
    }
}