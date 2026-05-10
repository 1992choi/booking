package com.example.booking.notification.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class UserClient {

    private final RestClient restClient;

    public UserSnapshot fetch(Long userId) {
        return restClient.get()
                .uri("/api/v1/internal/users/{id}", userId)
                .retrieve()
                .body(UserSnapshot.class);
    }
}