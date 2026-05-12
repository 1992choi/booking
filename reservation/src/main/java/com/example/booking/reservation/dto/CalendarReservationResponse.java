package com.example.booking.reservation.dto;

import com.example.booking.reservation.domain.Reservation;
import com.example.booking.reservation.domain.ReservationStatus;

import java.time.format.DateTimeFormatter;

public record CalendarReservationResponse(
        Long reservationId,
        String resourceName,
        String startTime,
        String endTime,
        ReservationStatus status
) {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static CalendarReservationResponse from(Reservation reservation) {
        return new CalendarReservationResponse(
                reservation.getId(),
                reservation.getResourceName(),
                reservation.getStartTime().format(TIME_FMT),
                reservation.getEndTime().format(TIME_FMT),
                reservation.getStatus()
        );
    }
}