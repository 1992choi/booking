package com.example.booking.reservation.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
}