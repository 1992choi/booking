package com.example.booking.api.merchant.controller;

import com.example.booking.api.merchant.domain.Merchant;
import com.example.booking.api.merchant.dto.MerchantCreateRequest;
import com.example.booking.api.merchant.dto.MerchantDetailResponse;
import com.example.booking.api.merchant.dto.MerchantResponse;
import com.example.booking.api.merchant.dto.MerchantSummaryResponse;
import com.example.booking.api.merchant.dto.MerchantUpdateRequest;
import com.example.booking.api.merchant.service.MerchantService;
import com.example.booking.api.resource.domain.ResourceRepository;
import com.example.booking.core.auth.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final ResourceRepository resourceRepository;

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
}
