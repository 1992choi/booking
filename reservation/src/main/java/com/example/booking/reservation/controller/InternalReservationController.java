package com.example.booking.reservation.controller;

import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.dto.CalendarReservationResponse;
import com.example.booking.reservation.dto.PageResponse;
import com.example.booking.reservation.dto.ReservationResponse;
import com.example.booking.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
public class InternalReservationController {

    private final ReservationService reservationService;

    @GetMapping("/api/v1/internal/reservations")
    public PageResponse<ReservationResponse> getAll(
            @RequestParam LocalDate date,
            @RequestParam(required = false) ReservationStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return reservationService.getAll(date, status, pageable);
    }

    @GetMapping("/api/v1/internal/reservations/calendar")
    public Map<LocalDate, List<CalendarReservationResponse>> getCalendar(
            @RequestParam int year,
            @RequestParam int month) {
        return reservationService.getCalendar(year, month);
    }

    @GetMapping("/api/v1/internal/reservations/{reservationId}")
    public ReservationResponse getById(@PathVariable Long reservationId) {
        return reservationService.getById(reservationId);
    }

    @PutMapping("/api/v1/internal/reservations/{reservationId}/confirm")
    public ReservationResponse confirm(@PathVariable Long reservationId) {
        return reservationService.confirm(reservationId);
    }

    @PutMapping("/api/v1/internal/reservations/{reservationId}/cancel")
    public ReservationResponse cancel(@PathVariable Long reservationId) {
        return reservationService.adminCancel(reservationId);
    }

    @GetMapping("/api/v1/internal/reservations/by-merchant")
    public PageResponse<ReservationResponse> getByMerchant(
            @RequestParam List<Long> resourceIds,
            @RequestParam(required = false) ReservationStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return reservationService.getByResourceIds(resourceIds, status, pageable);
    }
}