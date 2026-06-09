package com.example.booking.notification.service.channel;

import com.example.booking.notification.domain.NotificationChannel;
import com.example.booking.notification.domain.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.LOG;
    }

    @Override
    public void send(Long userId, String email, NotificationType type) {
        log.info("[Mock 알림] userId={}, email={}, type={}", userId, email, type);
    }

}