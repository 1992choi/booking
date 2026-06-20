package com.example.booking.reservation.merchant.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.reservation.admin.dto.AdminReservationPageResponse;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.merchant.dto.DailyMerchantStatsResponse;
import com.example.booking.reservation.merchant.dto.MerchantCreateRequest;
import com.example.booking.reservation.merchant.dto.MerchantDetailResponse;
import com.example.booking.reservation.merchant.dto.MerchantResponse;
import com.example.booking.reservation.merchant.dto.MerchantSummaryResponse;
import com.example.booking.reservation.merchant.dto.MerchantUpdateRequest;
import com.example.booking.reservation.merchant.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping("/api/v1/merchants")
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantResponse register(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody MerchantCreateRequest request) {
        return MerchantResponse.from(merchantService.register(principal.userId(), request));
    }

    @GetMapping("/api/v1/merchants/me")
    public List<MerchantResponse> getMyMerchants(@AuthenticationPrincipal AuthPrincipal principal) {
        return merchantService.getMyMerchants(principal.userId()).stream()
                .map(MerchantResponse::from)
                .toList();
    }

    @PutMapping("/api/v1/merchants/{merchantId}")
    public MerchantResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long merchantId,
                                   @Valid @RequestBody MerchantUpdateRequest request) {
        return MerchantResponse.from(merchantService.update(principal.userId(), merchantId, request));
    }

    @GetMapping("/api/v1/merchants")
    public List<MerchantSummaryResponse> getMerchants() {
        return merchantService.getAll().stream()
                .map(MerchantSummaryResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/merchants/{merchantId}")
    public MerchantDetailResponse getMerchant(@PathVariable Long merchantId) {
        return merchantService.getDetail(merchantId);
    }

    @GetMapping("/api/v1/merchants/{merchantId}/stats/daily")
    public List<DailyMerchantStatsResponse> getDailyStats(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long merchantId,
            @RequestParam int year,
            @RequestParam int month) {
        return merchantService.getDailyStats(principal.userId(), merchantId, year, month);
    }

    @GetMapping("/api/v1/merchants/{merchantId}/reservations")
    public AdminReservationPageResponse getMerchantReservations(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long merchantId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return merchantService.getMerchantReservations(principal.userId(), merchantId, status, page, size);
    }

}