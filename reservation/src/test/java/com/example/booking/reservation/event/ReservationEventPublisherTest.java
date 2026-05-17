package com.example.booking.reservation.event;

import com.example.booking.reservation.client.AvailableTimeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    AvailableTimeClient availableTimeClient;

    @InjectMocks
    ReservationEventPublisher publisher;

    ReservationCreatedDomainEvent createdEvent;
    ReservationCancelledDomainEvent cancelledEvent;

    @BeforeEach
    void setUp() {
        createdEvent = new ReservationCreatedDomainEvent(1L, 10L, 5L, 150000L, 99L);
        cancelledEvent = new ReservationCancelledDomainEvent(1L, 10L, 99L);
    }

    @Test
    void onReservationCreated_슬롯_BLOCKED_처리_후_카프카_발행() {
        publisher.onReservationCreated(createdEvent);

        verify(availableTimeClient).block(List.of(99L));
        verify(kafkaTemplate).send(eq("reservation.created"), any(ReservationCreatedKafkaEvent.class));
    }

    @Test
    void onReservationCreated_슬롯_BLOCKED_실패해도_카프카는_발행() {
        willThrow(new RuntimeException("api down")).given(availableTimeClient).block(any());

        publisher.onReservationCreated(createdEvent);

        verify(kafkaTemplate).send(eq("reservation.created"), any(ReservationCreatedKafkaEvent.class));
    }

    @Test
    void onReservationCancelled_슬롯_OPEN_복원_후_카프카_발행() {
        publisher.onReservationCancelled(cancelledEvent);

        verify(availableTimeClient).release(List.of(99L));
        verify(kafkaTemplate).send(eq("reservation.cancelled"), any(ReservationCancelledKafkaEvent.class));
    }

    @Test
    void onReservationCancelled_슬롯_복원_실패해도_카프카는_발행() {
        willThrow(new RuntimeException("api down")).given(availableTimeClient).release(any());

        publisher.onReservationCancelled(cancelledEvent);

        verify(kafkaTemplate).send(eq("reservation.cancelled"), any(ReservationCancelledKafkaEvent.class));
    }
}