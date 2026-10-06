package com.example.booking.reservation.event;

public record ReservationReminderKafkaEvent(
        Long reservationId,
        Long userId
) {}
