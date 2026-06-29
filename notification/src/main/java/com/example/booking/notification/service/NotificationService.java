package com.example.booking.notification.service;

import com.example.booking.notification.domain.Notification;
import com.example.booking.notification.domain.NotificationRepository;
import com.example.booking.notification.domain.NotificationStatus;
import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.service.channel.NotificationSender;
import com.example.booking.notification.user.domain.UserSync;
import com.example.booking.notification.user.domain.UserSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;
    private final UserSyncRepository userSyncRepository;

    @Transactional
    public void send(Long userId, Long reservationId, NotificationType type) {
        Notification notification = Notification.builder()
                .userId(userId)
                .reservationId(reservationId)
                .type(type)
                .channel(notificationSender.channel())
                .status(NotificationStatus.FAILED)
                .build();

        try {
            Optional<UserSync> userOpt = userSyncRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("유저 동기화 정보 없음 userId={}, 알림 발송 스킵", userId);
                notification.markFailed();
            } else {
                notificationSender.send(userId, userOpt.get().getEmail(), type);
                notification.markSent();
            }
        } catch (Exception e) {
            log.warn("알림 발송 실패 userId={}, type={}", userId, type, e);
            notification.markFailed();
        }

        notificationRepository.save(notification);
    }

    @Transactional
    public void sendAdminMessage(Long userId, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(NotificationType.ADMIN_MESSAGE)
                .message(message)
                .channel(notificationSender.channel())
                .status(NotificationStatus.FAILED)
                .build();

        try {
            Optional<UserSync> userOpt = userSyncRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("유저 동기화 정보 없음 userId={}, 관리자 메시지 발송 스킵", userId);
                notification.markFailed();
            } else {
                log.info("[관리자 메시지] userId={}, email={}, message={}", userId, userOpt.get().getEmail(), message);
                notificationSender.send(userId, userOpt.get().getEmail(), NotificationType.ADMIN_MESSAGE);
                notification.markSent();
            }
        } catch (Exception e) {
            log.warn("관리자 메시지 발송 실패 userId={}", userId, e);
            notification.markFailed();
        }

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getMyNotifications(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

}
