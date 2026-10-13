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
    @DisplayName("reservation.cancelled 처리 중 예외가 발생하면 그대로 전파된다 — 에러 핸들러가 재시도/DLT를 처리하도록")
    void onReservationCancelled_propagatesException() {
        String message = """
                {"reservationId":10,"userId":5}
                """;
        willThrow(new RuntimeException("send error")).given(notificationService).send(any(), any(), any());

        assertThatThrownBy(() -> consumer.onReservationCancelled(message))
                .isInstanceOf(RuntimeException.class);

        verify(notificationService).send(5L, 10L, NotificationType.CANCELLED);
    }

    @Test
    @DisplayName("reservation.cancelled 메시지가 잘못된 형식이면 예외가 전파된다")
    void onReservationCancelled_malformedMessage_throws() {
        assertThatThrownBy(() -> consumer.onReservationCancelled("not-a-json"))
                .isNotNull();

        verify(notificationService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("reservation.reminder 수신 시 REMINDER 알림을 발송한다")
    void onReservationReminder_sendsReminderNotification() {
        String message = """
                {"reservationId":10,"userId":5}
                """;

        consumer.onReservationReminder(message);

        verify(notificationService).send(5L, 10L, NotificationType.REMINDER);
    }

    @Test
    @DisplayName("reservation.reminder 메시지가 잘못된 형식이면 예외가 전파된다")
    void onReservationReminder_malformedMessage_throws() {
        assertThatThrownBy(() -> consumer.onReservationReminder("not-a-json"))
                .isNotNull();

        verify(notificationService, never()).send(any(), any(), any());
    }

}
