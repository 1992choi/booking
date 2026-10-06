package com.example.booking.reservation.event;

public record ReservationReminderDomainEvent(
        Long reservationId,
        Long userId
) {}
