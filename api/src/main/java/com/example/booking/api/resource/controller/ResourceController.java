package com.example.booking.api.resource.controller;

import com.example.booking.api.resource.domain.AvailableTime;
import com.example.booking.api.resource.domain.Resource;
import com.example.booking.api.resource.dto.AvailableTimeCreateRequest;
import com.example.booking.api.resource.dto.AvailableTimeResponse;
import com.example.booking.api.resource.dto.ResourceCreateRequest;
import com.example.booking.api.resource.dto.ResourceResponse;
import com.example.booking.api.resource.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/api/v1/owners/{ownerId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse register(@PathVariable Long ownerId,
                                     @Valid @RequestBody ResourceCreateRequest request) {
        Resource resource = resourceService.register(ownerId, request);
        return ResourceResponse.from(resource);
    }

    @PostMapping("/api/v1/resources/{resourceId}/available-times")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailableTimeResponse addAvailableTime(@PathVariable Long resourceId,
                                                  @Valid @RequestBody AvailableTimeCreateRequest request) {
        AvailableTime availableTime = resourceService.addAvailableTime(resourceId, request);
        return AvailableTimeResponse.from(availableTime);
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