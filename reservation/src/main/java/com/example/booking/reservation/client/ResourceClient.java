package com.example.booking.reservation.client;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ResourceClient {

    private final RestClient restClient;

    public ResourceSnapshot fetch(Long resourceId) {
        return restClient.get()
                .uri("/api/v1/internal/resources/{id}", resourceId)
                .retrieve()
                .body(ResourceSnapshot.class);
    }

    public List<AvailableTimeSnapshot> fetchAvailableTimes(List<Long> ids) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/internal/available-times")
                        .queryParam("ids", ids)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}