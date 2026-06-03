package com.example.booking.payment.event;

import com.example.booking.payment.service.PaymentService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation.created", groupId = "payment-group")
    public void onReservationCreated(String message) {
        try {
            ReservationCreatedKafkaEvent event = objectMapper.readValue(message, ReservationCreatedKafkaEvent.class);
            paymentService.process(event);
            log.info("reservation.created 처리 완료 reservationId={}", event.reservationId());
        } catch (Exception e) {
            log.error("reservation.created 처리 실패: {}", message, e);
        }
    }
}