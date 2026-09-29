package com.example.booking.notification.event;

import com.example.booking.notification.dto.NotificationResponse;

public record NotificationCreatedDomainEvent(
        Long userId,
        NotificationResponse notification
) {
}
