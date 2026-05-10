package com.example.booking.reservation.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.dto.CreateReservationRequest;
import com.example.booking.reservation.dto.PageResponse;
import com.example.booking.reservation.dto.ReservationResponse;
import com.example.booking.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(principal.userId(), request);
    }

    @GetMapping("/{reservationId}")
    public ReservationResponse getById(@PathVariable Long reservationId) {
        return reservationService.getById(reservationId);
    }

    @GetMapping("/me")
    public PageResponse<ReservationResponse> getMyReservations(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false, defaultValue = "PENDING") ReservationStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return reservationService.getMyReservations(principal.userId(), status, pageable);
    }

    @PutMapping("/{reservationId}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable Long reservationId) {
        return reservationService.cancel(principal.userId(), reservationId);
    }
}