package com.example.booking.reservation.event;

public record ReservationCancelledDomainEvent(
        Long reservationId,
        Long userId,
        Long availableTimeId
) {}