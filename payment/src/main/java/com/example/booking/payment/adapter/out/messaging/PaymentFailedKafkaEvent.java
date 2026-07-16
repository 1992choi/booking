package com.example.booking.payment.adapter.out.messaging;

public record PaymentFailedKafkaEvent(
        Long reservationId,
        Long userId
) {}