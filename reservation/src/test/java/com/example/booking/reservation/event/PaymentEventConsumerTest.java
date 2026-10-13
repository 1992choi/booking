package com.example.booking.reservation.event;

import com.example.booking.reservation.service.ReservationService;
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
    ReservationService reservationService;

    ObjectMapper objectMapper = new ObjectMapper();

    PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventConsumer(reservationService, objectMapper);
    }

    @Test
    @DisplayName("payment.completed 수신 시 예약을 확정 처리한다")
    void onPaymentCompleted_confirmsReservation() {
        String message = """
                {"paymentId":1,"reservationId":10,"userId":5}
                """;

        consumer.onPaymentCompleted(message);

        verify(reservationService).confirm(10L);
    }

    @Test
    @DisplayName("payment.completed 처리 중 예외가 발생하면 그대로 전파된다 — 에러 핸들러가 재시도/DLT를 처리하도록")
    void onPaymentCompleted_propagatesException() {
        String message = """
                {"paymentId":1,"reservationId":10,"userId":5}
                """;
        willThrow(new RuntimeException("db error")).given(reservationService).confirm(any());

        assertThatThrownBy(() -> consumer.onPaymentCompleted(message))
                .isInstanceOf(RuntimeException.class);

        verify(reservationService).confirm(10L);
    }

    @Test
    @DisplayName("payment.completed 메시지가 잘못된 형식이면 예외가 전파된다")
    void onPaymentCompleted_malformedMessage_throws() {
        assertThatThrownBy(() -> consumer.onPaymentCompleted("not-a-json"))
                .isNotNull();

        verify(reservationService, never()).confirm(any());
    }

    @Test
    @DisplayName("payment.failed 수신 시 결제 실패로 예약을 취소한다")
    void onPaymentFailed_cancelsReservation() {
        String message = """
                {"reservationId":20,"userId":7}
                """;

        consumer.onPaymentFailed(message);

        verify(reservationService).cancelByPaymentFailure(20L);
    }

    @Test
    @DisplayName("payment.failed 처리 중 예외가 발생하면 그대로 전파된다 — 에러 핸들러가 재시도/DLT를 처리하도록")
    void onPaymentFailed_propagatesException() {
        String message = """
                {"reservationId":20,"userId":7}
                """;
        willThrow(new RuntimeException("db error")).given(reservationService).cancelByPaymentFailure(any());

        assertThatThrownBy(() -> consumer.onPaymentFailed(message))
                .isInstanceOf(RuntimeException.class);

        verify(reservationService).cancelByPaymentFailure(20L);
    }

}
