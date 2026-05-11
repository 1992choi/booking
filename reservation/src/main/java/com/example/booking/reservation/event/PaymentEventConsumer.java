package com.example.booking.reservation.event;

import com.example.booking.reservation.service.ReservationService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.failed", groupId = "reservation-group")
    public void onPaymentFailed(String message) {
        try {
            PaymentFailedKafkaEvent event = objectMapper.readValue(message, PaymentFailedKafkaEvent.class);
            reservationService.cancelByPaymentFailure(event.reservationId());
        } catch (Exception e) {
            log.error("payment.failed 처리 실패: {}", message, e);
        }
    }
}