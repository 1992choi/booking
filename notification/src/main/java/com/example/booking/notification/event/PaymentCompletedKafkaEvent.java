package com.example.booking.notification.event;

public record PaymentCompletedKafkaEvent(
        Long paymentId,
        Long reservationId,
        Long userId
) {}