package com.example.booking.reservation.resource.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.reservation.resource.domain.AvailableTime;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.dto.AvailableTimeCreateRequest;
import com.example.booking.reservation.resource.dto.AvailableTimeResponse;
import com.example.booking.reservation.resource.dto.AvailableTimeUpdateRequest;
import com.example.booking.reservation.resource.dto.ResourceCreateRequest;
import com.example.booking.reservation.resource.dto.ResourceResponse;
import com.example.booking.reservation.resource.dto.ResourceUpdateRequest;
import com.example.booking.reservation.resource.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping("/api/v1/merchants/{merchantId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse register(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable Long merchantId,
                                     @Valid @RequestBody ResourceCreateRequest request) {
        Resource resource = resourceService.register(principal.userId(), merchantId, request);

        return ResourceResponse.from(resource);
    }

    @PostMapping("/api/v1/resources/{resourceId}/available-times")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailableTimeResponse addAvailableTime(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long resourceId,
                                                  @Valid @RequestBody AvailableTimeCreateRequest request) {
        AvailableTime availableTime = resourceService.addAvailableTime(principal.userId(), resourceId, request);

        return AvailableTimeResponse.from(availableTime);
    }

    @PutMapping("/api/v1/resources/{resourceId}")
    public ResourceResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long resourceId,
                                   @Valid @RequestBody ResourceUpdateRequest request) {
        return ResourceResponse.from(resourceService.update(principal.userId(), resourceId, request));
    }

    @DeleteMapping("/api/v1/resources/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable Long resourceId) {
        resourceService.delete(principal.userId(), resourceId);
    }

    @PutMapping("/api/v1/available-times/{id}")
    public AvailableTimeResponse updateAvailableTime(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody AvailableTimeUpdateRequest request) {
        return AvailableTimeResponse.from(resourceService.updateAvailableTime(principal.userId(), id, request));
    }

    @DeleteMapping("/api/v1/available-times/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvailableTime(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Long id) {
        resourceService.deleteAvailableTime(principal.userId(), id);
    }

    @GetMapping("/api/v1/resources/{resourceId}/available-times")
    public List<AvailableTimeResponse> getAvailableTimes(
            @PathVariable Long resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return resourceService.getAvailableTimes(resourceId, date)
                .stream()
                .map(AvailableTimeResponse::from)
                .toList();
    }

}
