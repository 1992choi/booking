package com.example.booking.api.owner.controller;

import com.example.booking.api.owner.domain.Owner;
import com.example.booking.api.owner.dto.OwnerCreateRequest;
import com.example.booking.api.owner.dto.OwnerDetailResponse;
import com.example.booking.api.owner.dto.OwnerResponse;
import com.example.booking.api.owner.dto.OwnerSummaryResponse;
import com.example.booking.api.owner.dto.OwnerUpdateRequest;
import com.example.booking.api.owner.service.OwnerService;
import com.example.booking.api.resource.domain.Resource;
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
public class OwnerController {

    private final OwnerService ownerService;
    private final ResourceRepository resourceRepository;

    @PostMapping("/api/v1/owners")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerResponse register(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody OwnerCreateRequest request) {
        Owner owner = ownerService.register(principal.userId(), request);
        return OwnerResponse.from(owner);
    }

    @PutMapping("/api/v1/owners/me")
    public OwnerResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                @Valid @RequestBody OwnerUpdateRequest request) {
        return OwnerResponse.from(ownerService.update(principal.userId(), request));
    }

    @GetMapping("/api/v1/owners/me")
    public OwnerResponse getMyOwner(@AuthenticationPrincipal AuthPrincipal principal) {
        Owner owner = ownerService.getByUserId(principal.userId());
        return OwnerResponse.from(owner);
    }

    @GetMapping("/api/v1/owners")
    public List<OwnerSummaryResponse> getOwners() {
        return ownerService.getAll().stream()
                .map(OwnerSummaryResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/owners/{ownerId}")
    public OwnerDetailResponse getOwner(@PathVariable Long ownerId) {
        Owner owner = ownerService.getById(ownerId);
        List<Resource> resources = resourceRepository.findAllByOwnerId(ownerId);
        return OwnerDetailResponse.from(owner, resources);
    }
}
