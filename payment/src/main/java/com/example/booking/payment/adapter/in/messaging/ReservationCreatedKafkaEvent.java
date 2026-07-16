package com.example.booking.payment.adapter.in.messaging;

public record ReservationCreatedKafkaEvent(
        Long reservationId,
        Long userId,
        Long resourceId,
        Long amount
) {}