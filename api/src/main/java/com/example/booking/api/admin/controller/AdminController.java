package com.example.booking.api.admin.controller;

import com.example.booking.api.admin.client.ReservationClient;
import com.example.booking.api.admin.dto.AdminCalendarEntry;
import com.example.booking.api.admin.dto.AdminReservationResponse;
import jakarta.servlet.http.HttpServletRequest;
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

    private final ReservationClient reservationClient;

    @GetMapping("/api/v1/admin/reservations/calendar")
    public Map<LocalDate, List<AdminCalendarEntry>> getCalendar(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest request) {
        return reservationClient.getCalendar(year, month, request.getHeader("Authorization"));
    }

    @PutMapping("/api/v1/admin/reservations/{reservationId}/confirm")
    public AdminReservationResponse confirm(
            @PathVariable Long reservationId,
            HttpServletRequest request) {
        return reservationClient.confirm(reservationId, request.getHeader("Authorization"));
    }

    @PutMapping("/api/v1/admin/reservations/{reservationId}/cancel")
    public AdminReservationResponse cancel(
            @PathVariable Long reservationId,
            HttpServletRequest request) {
        return reservationClient.cancel(reservationId, request.getHeader("Authorization"));
    }
}