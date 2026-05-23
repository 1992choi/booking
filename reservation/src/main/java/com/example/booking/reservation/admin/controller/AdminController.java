package com.example.booking.reservation.admin.controller;

import com.example.booking.reservation.admin.dto.AdminReservationResponse;
import com.example.booking.reservation.dto.CalendarReservationResponse;
import com.example.booking.reservation.dto.ReservationResponse;
import com.example.booking.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminController {

    private final ReservationService reservationService;

    @GetMapping("/api/v1/admin/reservations/calendar")
    public Map<LocalDate, List<CalendarReservationResponse>> getCalendar(
            @RequestParam int year,
            @RequestParam int month) {
        return reservationService.getCalendar(year, month);
    }

    @PutMapping("/api/v1/admin/reservations/{reservationId}/confirm")
    public AdminReservationResponse confirm(@PathVariable Long reservationId) {
        return toAdminResponse(reservationService.confirm(reservationId));
    }

    @PutMapping("/api/v1/admin/reservations/{reservationId}/cancel")
    public AdminReservationResponse cancel(@PathVariable Long reservationId) {
        return toAdminResponse(reservationService.adminCancel(reservationId));
    }

    private AdminReservationResponse toAdminResponse(ReservationResponse r) {
        return new AdminReservationResponse(
                r.id(), r.status().name(), r.resourceName(),
                r.startTime(), r.endTime(), r.headCount(), r.amount(), r.userId(), null);
    }
}
