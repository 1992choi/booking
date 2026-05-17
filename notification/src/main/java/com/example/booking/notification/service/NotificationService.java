package com.example.booking.notification.service;

import com.example.booking.notification.client.UserClient;
import com.example.booking.notification.client.UserSnapshot;
import com.example.booking.notification.domain.Notification;
import com.example.booking.notification.domain.NotificationRepository;
import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.service.channel.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;
    private final UserClient userClient;

    @Transactional
    public void send(Long userId, Long reservationId, NotificationType type) {
        Notification notification = Notification.builder()
                .userId(userId)
                .reservationId(reservationId)
                .type(type)
                .channel(notificationSender.channel())
                .status(com.example.booking.notification.domain.NotificationStatus.FAILED)
                .build();

        try {
            UserSnapshot user = userClient.fetch(userId);
            notificationSender.send(userId, user.email(), type);
            notification.markSent();
        } catch (Exception e) {
            log.warn("알림 발송 실패 userId={}, type={}", userId, type, e);
            notification.markFailed();
        }

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getMyNotifications(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }
}