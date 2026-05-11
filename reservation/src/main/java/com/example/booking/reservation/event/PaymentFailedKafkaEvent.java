package com.example.booking.reservation.event;

public record PaymentFailedKafkaEvent(
        Long reservationId,
        Long userId
) {}