package com.example.booking.reservation.event;

public record PaymentCompletedKafkaEvent(
        Long paymentId,
        Long reservationId,
        Long userId
) {}