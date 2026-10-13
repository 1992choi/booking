package com.example.booking.notification.event;

import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.service.NotificationService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000), kafkaTemplate = "retryKafkaTemplate")
    @KafkaListener(topics = "reservation.cancelled", groupId = "notification-group")
    public void onReservationCancelled(String message) {
        ReservationCancelledKafkaEvent event = objectMapper.readValue(message, ReservationCancelledKafkaEvent.class);

        notificationService.send(event.userId(), event.reservationId(), NotificationType.CANCELLED);
        log.info("reservation.cancelled 처리 완료 reservationId={}, userId={}", event.reservationId(), event.userId());
    }

    @RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000), kafkaTemplate = "retryKafkaTemplate")
    @KafkaListener(topics = "reservation.reminder", groupId = "notification-group")
    public void onReservationReminder(String message) {
        ReservationReminderKafkaEvent event = objectMapper.readValue(message, ReservationReminderKafkaEvent.class);

        notificationService.send(event.userId(), event.reservationId(), NotificationType.REMINDER);
        log.info("reservation.reminder 처리 완료 reservationId={}, userId={}", event.reservationId(), event.userId());
    }

    @DltHandler
    public void onDlt(String message, @Header(KafkaHeaders.ORIGINAL_TOPIC) String originalTopic) {
        log.error("DLT 수신 — 재시도 모두 실패 topic={}, message={}", originalTopic, message);
    }

}
