package com.example.booking.reservation.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AvailableTimeClient {

    private final RestClient restClient;

    public void block(List<Long> ids) {
        restClient.put()
                .uri("/api/v1/internal/available-times/block")
                .body(ids)
                .retrieve()
                .toBodilessEntity();
    }

    public void release(List<Long> ids) {
        restClient.put()
                .uri("/api/v1/internal/available-times/release")
                .body(ids)
                .retrieve()
                .toBodilessEntity();
    }
}