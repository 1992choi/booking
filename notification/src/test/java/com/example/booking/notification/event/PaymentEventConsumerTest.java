package com.example.booking.notification.event;

import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    NotificationService notificationService;

    ObjectMapper objectMapper = new ObjectMapper();

    PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventConsumer(notificationService, objectMapper);
    }

    @Test
    @DisplayName("payment.completed 수신 시 CONFIRMED 알림을 발송한다")
    void onPaymentCompleted_sendsConfirmedNotification() {
        String message = """
                {"paymentId":1,"reservationId":10,"userId":5}
                """;

        consumer.onPaymentCompleted(message);

        verify(notificationService).send(5L, 10L, NotificationType.CONFIRMED);
    }

    @Test
    @DisplayName("payment.completed 처리 중 예외가 발생하면 그대로 전파된다 — 에러 핸들러가 재시도/DLT를 처리하도록")
    void onPaymentCompleted_propagatesException() {
        String message = """
                {"paymentId":1,"reservationId":10,"userId":5}
                """;
        willThrow(new RuntimeException("send error")).given(notificationService).send(any(), any(), any());

        assertThatThrownBy(() -> consumer.onPaymentCompleted(message))
                .isInstanceOf(RuntimeException.class);

        verify(notificationService).send(5L, 10L, NotificationType.CONFIRMED);
    }

    @Test
    @DisplayName("payment.completed 메시지가 잘못된 형식이면 예외가 전파된다")
    void onPaymentCompleted_malformedMessage_throws() {
        assertThatThrownBy(() -> consumer.onPaymentCompleted("not-a-json"))
                .isNotNull();

        verify(notificationService, never()).send(any(), any(), any());
    }

}
