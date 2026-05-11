package com.example.booking.reservation.event;

public record ReservationCancelledKafkaEvent(
        Long reservationId,
        Long userId
) {}