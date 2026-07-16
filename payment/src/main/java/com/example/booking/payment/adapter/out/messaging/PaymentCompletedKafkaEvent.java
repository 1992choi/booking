package com.example.booking.payment.adapter.out.messaging;

public record PaymentCompletedKafkaEvent(
        Long paymentId,
        Long reservationId,
        Long userId
) {}