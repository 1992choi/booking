package com.example.booking.payment.event;

public record PaymentCompletedKafkaEvent(
        Long paymentId,
        Long reservationId,
        Long userId
) {}