package com.example.booking.notification.event;

import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.service.NotificationService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.completed", groupId = "notification-group")
    public void onPaymentCompleted(String message) {
        try {
            PaymentCompletedKafkaEvent event = objectMapper.readValue(message, PaymentCompletedKafkaEvent.class);

            notificationService.send(event.userId(), event.reservationId(), NotificationType.CONFIRMED);
            log.info("payment.completed 처리 완료 reservationId={}, userId={}", event.reservationId(), event.userId());
        } catch (Exception e) {
            log.error("payment.completed 처리 실패: {}", message, e);
        }
    }

}