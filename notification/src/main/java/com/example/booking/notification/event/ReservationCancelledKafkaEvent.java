package com.example.booking.notification.event;

public record ReservationCancelledKafkaEvent(
        Long reservationId,
        Long userId
) {}