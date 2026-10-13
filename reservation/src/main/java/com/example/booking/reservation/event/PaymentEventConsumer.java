package com.example.booking.reservation.event;

import com.example.booking.reservation.service.ReservationService;
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
public class PaymentEventConsumer {

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000), kafkaTemplate = "retryKafkaTemplate")
    @KafkaListener(topics = "payment.completed", groupId = "reservation-group")
    public void onPaymentCompleted(String message) {
        PaymentCompletedKafkaEvent event = objectMapper.readValue(message, PaymentCompletedKafkaEvent.class);
        reservationService.confirm(event.reservationId());
    }

    @RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000), kafkaTemplate = "retryKafkaTemplate")
    @KafkaListener(topics = "payment.failed", groupId = "reservation-group")
    public void onPaymentFailed(String message) {
        PaymentFailedKafkaEvent event = objectMapper.readValue(message, PaymentFailedKafkaEvent.class);
        reservationService.cancelByPaymentFailure(event.reservationId());
    }

    @DltHandler
    public void onDlt(String message, @Header(KafkaHeaders.ORIGINAL_TOPIC) String originalTopic) {
        log.error("DLT 수신 — 재시도 모두 실패 topic={}, message={}", originalTopic, message);
    }

}