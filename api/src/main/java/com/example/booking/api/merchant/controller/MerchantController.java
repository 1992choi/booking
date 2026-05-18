package com.example.booking.api.merchant.controller;

import com.example.booking.api.admin.client.ReservationClient;
import com.example.booking.api.admin.dto.AdminReservationPageResponse;
import com.example.booking.api.merchant.domain.Merchant;
import com.example.booking.api.merchant.dto.MerchantCreateRequest;
import com.example.booking.api.merchant.dto.MerchantDetailResponse;
import com.example.booking.api.merchant.dto.MerchantResponse;
import com.example.booking.api.merchant.dto.MerchantSummaryResponse;
import com.example.booking.api.merchant.dto.MerchantUpdateRequest;
import com.example.booking.api.merchant.service.MerchantService;
import com.example.booking.api.resource.domain.Resource;
import com.example.booking.api.resource.domain.ResourceRepository;
import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ResourceRepository resourceRepository;
    private final ReservationClient reservationClient;

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
        Merchant merchant = merchantService.getById(merchantId);
        return MerchantDetailResponse.from(merchant, resourceRepository.findAllByMerchantId(merchantId));
    }

    @GetMapping("/api/v1/merchants/{merchantId}/reservations")
    public AdminReservationPageResponse getMerchantReservations(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Merchant merchant = merchantService.getById(merchantId);
        if (!merchant.getUserId().equals(principal.userId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        List<Long> resourceIds = resourceRepository.findAllByMerchantId(merchantId).stream()
                .map(Resource::getId)
                .toList();
        if (resourceIds.isEmpty()) {
            return new AdminReservationPageResponse(List.of(), page, size, 0L, 0);
        }
        return reservationClient.getByMerchant(resourceIds, status, page, size, request.getHeader("Authorization"));
    }
}
