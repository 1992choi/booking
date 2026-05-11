package com.example.booking.payment.event;

public record PaymentFailedKafkaEvent(
        Long reservationId,
        Long userId
) {}