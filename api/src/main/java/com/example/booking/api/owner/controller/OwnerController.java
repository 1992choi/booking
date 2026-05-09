package com.example.booking.api.owner.controller;

import com.example.booking.api.owner.domain.Owner;
import com.example.booking.api.owner.dto.OwnerCreateRequest;
import com.example.booking.api.owner.dto.OwnerResponse;
import com.example.booking.api.owner.service.OwnerService;
import com.example.booking.core.auth.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @PostMapping("/api/v1/owners")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerResponse register(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody OwnerCreateRequest request) {
        Owner owner = ownerService.register(principal.userId(), request);
        return OwnerResponse.from(owner);
    }

    @GetMapping("/api/v1/owners/me")
    public OwnerResponse getMyOwner(@AuthenticationPrincipal AuthPrincipal principal) {
        Owner owner = ownerService.getByUserId(principal.userId());
        return OwnerResponse.from(owner);
    }
}
