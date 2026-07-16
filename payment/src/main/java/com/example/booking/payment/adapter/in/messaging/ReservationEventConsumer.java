package com.example.booking.payment.adapter.in.messaging;

import com.example.booking.payment.application.port.in.ChargePaymentCommand;
import com.example.booking.payment.application.port.in.ChargePaymentUseCase;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventConsumer {

    private final ChargePaymentUseCase chargePaymentUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation.created", groupId = "payment-group")
    public void onReservationCreated(String message) {
        ReservationCreatedKafkaEvent event = objectMapper.readValue(message, ReservationCreatedKafkaEvent.class);

        if (event.amount() < 0) {
            log.warn("음수 금액 감지 — 재시도 대기 reservationId={}, amount={}", event.reservationId(), event.amount());
            throw new IllegalArgumentException("음수 금액은 처리할 수 없습니다: " + event.amount());
        }

        chargePaymentUseCase.charge(new ChargePaymentCommand(event.reservationId(), event.userId(), event.amount()));
        log.info("reservation.created 처리 완료 reservationId={}", event.reservationId());
    }

}
