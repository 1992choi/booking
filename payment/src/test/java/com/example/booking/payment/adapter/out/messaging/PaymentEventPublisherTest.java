package com.example.booking.payment.adapter.out.messaging;

import com.example.booking.payment.application.event.PaymentCompletedDomainEvent;
import com.example.booking.payment.application.event.PaymentFailedDomainEvent;
import com.example.booking.payment.domain.Payment;
import com.example.booking.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    PaymentEventPublisher publisher;

    @Test
    @DisplayName("결제 완료 시 payment.completed 이벤트를 발행한다")
    void onPaymentCompleted_publishesToKafka() {
        Payment payment = Payment.reconstruct(1L, 10L, 5L, 50000L,
                PaymentStatus.COMPLETED, "PG-1", null, null);

        publisher.onPaymentCompleted(new PaymentCompletedDomainEvent(payment));

        verify(kafkaTemplate).send(eq("payment.completed"), eq(new PaymentCompletedKafkaEvent(1L, 10L, 5L)));
    }

    @Test
    @DisplayName("결제 실패 시 payment.failed 이벤트를 발행한다")
    void onPaymentFailed_publishesToKafka() {
        Payment payment = Payment.reconstruct(1L, 10L, 5L, 50000L,
                PaymentStatus.FAILED, null, null, "카드 한도 초과");

        publisher.onPaymentFailed(new PaymentFailedDomainEvent(payment));

        verify(kafkaTemplate).send(eq("payment.failed"), eq(new PaymentFailedKafkaEvent(10L, 5L)));
    }

}
