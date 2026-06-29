package com.example.booking.notification.dto;

import com.example.booking.notification.domain.Notification;
import com.example.booking.notification.domain.NotificationChannel;
import com.example.booking.notification.domain.NotificationStatus;
import com.example.booking.notification.domain.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long reservationId,
        String message,
        NotificationType type,
        NotificationChannel channel,
        NotificationStatus status,
        LocalDateTime sentAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getReservationId(),
                notification.getMessage(),
                notification.getType(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getSentAt()
        );
    }
}