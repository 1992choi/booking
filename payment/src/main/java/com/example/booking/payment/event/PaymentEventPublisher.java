package com.example.booking.payment.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedDomainEvent event) {
        kafkaTemplate.send("payment.completed",
                new PaymentCompletedKafkaEvent(
                        event.payment().getId(),
                        event.payment().getReservationId(),
                        event.payment().getUserId()
                ));
        log.info("payment.completed 발행 paymentId={}, reservationId={}", event.payment().getId(), event.payment().getReservationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedDomainEvent event) {
        kafkaTemplate.send("payment.failed",
                new PaymentFailedKafkaEvent(
                        event.payment().getReservationId(),
                        event.payment().getUserId()
                ));
        log.info("payment.failed 발행 reservationId={}", event.payment().getReservationId());
    }
}