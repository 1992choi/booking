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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationEventConsumerTest {

    @Mock
    NotificationService notificationService;

    ObjectMapper objectMapper = new ObjectMapper();

    ReservationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReservationEventConsumer(notificationService, objectMapper);
    }

    @Test
    @DisplayName("reservation.cancelled 수신 시 CANCELLED 알림을 발송한다")
    void onReservationCancelled_sendsCancelledNotification() {
        String message = """
                {"reservationId":10,"userId":5}
                """;

        consumer.onReservationCancelled(message);

        verify(notificationService).send(5L, 10L, NotificationType.CANCELLED);
    }

    @Test
    @DisplayName("reservation.cancelled 처리 중 예외가 발생해도 전파되지 않는다")
    void onReservationCancelled_swallowsException() {
        String message = """
                {"reservationId":10,"userId":5}
                """;
        willThrow(new RuntimeException("send error")).given(notificationService).send(any(), any(), any());

        consumer.onReservationCancelled(message);

        verify(notificationService).send(5L, 10L, NotificationType.CANCELLED);
    }

    @Test
    @DisplayName("reservation.cancelled 메시지가 잘못된 형식이면 예외 없이 무시한다")
    void onReservationCancelled_malformedMessage_doesNotThrow() {
        consumer.onReservationCancelled("not-a-json");

        verify(notificationService, never()).send(any(), any(), any());
    }

}
