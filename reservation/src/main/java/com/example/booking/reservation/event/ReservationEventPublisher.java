package com.example.booking.reservation.event;

import com.example.booking.reservation.client.AvailableTimeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AvailableTimeClient availableTimeClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedDomainEvent event) {
        try {
            availableTimeClient.block(List.of(event.availableTimeId()));
        } catch (Exception e) {
            log.error("슬롯 BLOCKED 처리 실패 availableTimeId={}", event.availableTimeId(), e);
        }
        kafkaTemplate.send("reservation.created",
                new ReservationCreatedKafkaEvent(
                        event.reservationId(),
                        event.userId(),
                        event.resourceId(),
                        event.amount()
                ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCancelled(ReservationCancelledDomainEvent event) {
        try {
            availableTimeClient.release(List.of(event.availableTimeId()));
        } catch (Exception e) {
            log.error("슬롯 OPEN 복원 실패 availableTimeId={}", event.availableTimeId(), e);
        }
        kafkaTemplate.send("reservation.cancelled",
                new ReservationCancelledKafkaEvent(
                        event.reservationId(),
                        event.userId()
                ));
    }
}