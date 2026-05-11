package com.example.booking.payment.event;

public record ReservationCreatedKafkaEvent(
        Long reservationId,
        Long userId,
        Long resourceId,
        Long amount
) {}