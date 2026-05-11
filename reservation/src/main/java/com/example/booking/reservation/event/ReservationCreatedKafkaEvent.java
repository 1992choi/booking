package com.example.booking.reservation.event;

public record ReservationCreatedKafkaEvent(
        Long reservationId,
        Long userId,
        Long resourceId,
        Long amount
) {}