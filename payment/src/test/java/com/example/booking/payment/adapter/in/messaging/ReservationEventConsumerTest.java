package com.example.booking.payment.adapter.in.messaging;

import com.example.booking.payment.application.port.in.ChargePaymentCommand;
import com.example.booking.payment.application.port.in.ChargePaymentUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationEventConsumerTest {

    @Mock
    ChargePaymentUseCase chargePaymentUseCase;

    ObjectMapper objectMapper = new ObjectMapper();

    ReservationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReservationEventConsumer(chargePaymentUseCase, objectMapper);
    }

    @Test
    @DisplayName("reservation.created 수신 시 결제를 요청한다")
    void onReservationCreated_chargesPayment() {
        String message = """
                {"reservationId":10,"userId":5,"resourceId":1,"amount":50000}
                """;

        consumer.onReservationCreated(message);

        verify(chargePaymentUseCase).charge(new ChargePaymentCommand(10L, 5L, 50000L));
    }

    @Test
    @DisplayName("금액이 음수면 결제를 요청하지 않고 예외를 던진다")
    void onReservationCreated_negativeAmount_throwsAndSkipsCharge() {
        String message = """
                {"reservationId":10,"userId":5,"resourceId":1,"amount":-1}
                """;

        assertThatThrownBy(() -> consumer.onReservationCreated(message))
                .isInstanceOf(IllegalArgumentException.class);

        verify(chargePaymentUseCase, never()).charge(any());
    }

}
