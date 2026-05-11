package com.example.booking.payment.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedDomainEvent event) {
        kafkaTemplate.send("payment.failed",
                new PaymentFailedKafkaEvent(
                        event.payment().getReservationId(),
                        event.payment().getUserId()
                ));
    }
}