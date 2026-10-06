package com.example.booking.notification.event;

public record ReservationReminderKafkaEvent(
        Long reservationId,
        Long userId
) {}
