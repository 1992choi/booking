package com.example.booking.reservation.event;

public record ReservationCreatedDomainEvent(
        Long reservationId,
        Long userId,
        Long resourceId,
        Long amount,
        Long availableTimeId
) {}