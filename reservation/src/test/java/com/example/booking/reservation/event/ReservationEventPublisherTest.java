package com.example.booking.reservation.event;

import com.example.booking.reservation.resource.domain.AvailableTime;
import com.example.booking.reservation.resource.domain.AvailableTimeRepository;
import com.example.booking.reservation.resource.domain.AvailableTimeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    AvailableTimeRepository availableTimeRepository;

    @InjectMocks
    ReservationEventPublisher publisher;

    ReservationCreatedDomainEvent createdEvent;
    ReservationCancelledDomainEvent cancelledEvent;
    AvailableTime slot;

    @BeforeEach
    void setUp() {
        createdEvent = new ReservationCreatedDomainEvent(1L, 10L, 5L, 150000L, 99L);
        cancelledEvent = new ReservationCancelledDomainEvent(1L, 10L, 99L);
        slot = AvailableTime.builder()
                .resourceId(5L)
                .startTime(LocalDateTime.of(2026, 6, 1, 14, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 15, 0))
                .status(AvailableTimeStatus.OPEN)
                .build();
    }

    @Test
    void onReservationCreated_슬롯_BLOCKED_처리_후_카프카_발행() {
        given(availableTimeRepository.findById(99L)).willReturn(Optional.of(slot));

        publisher.onReservationCreated(createdEvent);

        verify(availableTimeRepository).findById(99L);
        verify(availableTimeRepository).save(slot);
        verify(kafkaTemplate).send(eq("reservation.created"), any(ReservationCreatedKafkaEvent.class));
    }

    @Test
    void onReservationCreated_슬롯_BLOCKED_실패해도_카프카는_발행() {
        willThrow(new RuntimeException("db error")).given(availableTimeRepository).findById(any());

        publisher.onReservationCreated(createdEvent);

        verify(kafkaTemplate).send(eq("reservation.created"), any(ReservationCreatedKafkaEvent.class));
    }

    @Test
    void onReservationCancelled_슬롯_OPEN_복원_후_카프카_발행() {
        given(availableTimeRepository.findById(99L)).willReturn(Optional.of(slot));

        publisher.onReservationCancelled(cancelledEvent);

        verify(availableTimeRepository).findById(99L);
        verify(availableTimeRepository).save(slot);
        verify(kafkaTemplate).send(eq("reservation.cancelled"), any(ReservationCancelledKafkaEvent.class));
    }

    @Test
    void onReservationCancelled_슬롯_복원_실패해도_카프카는_발행() {
        willThrow(new RuntimeException("db error")).given(availableTimeRepository).findById(any());

        publisher.onReservationCancelled(cancelledEvent);

        verify(kafkaTemplate).send(eq("reservation.cancelled"), any(ReservationCancelledKafkaEvent.class));
    }
}
