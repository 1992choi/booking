package com.example.booking.notification.service.channel;

import com.example.booking.notification.domain.NotificationChannel;
import com.example.booking.notification.domain.NotificationType;

public interface NotificationSender {
    NotificationChannel channel();
    void send(Long userId, String email, NotificationType type);
}