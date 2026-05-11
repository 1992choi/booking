package com.example.booking.reservation.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReservationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedDomainEvent event) {
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
        kafkaTemplate.send("reservation.cancelled",
                new ReservationCancelledKafkaEvent(
                        event.reservationId(),
                        event.userId()
                ));
    }
}