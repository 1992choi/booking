package com.example.booking.api.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class NotificationClient {

    private final RestClient notificationRestClient;

    public void sendAdminMessage(Long userId, String message) {
        notificationRestClient.post()
                .uri("/api/v1/internal/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AdminMessagePayload(userId, message))
                .retrieve()
                .toBodilessEntity();
    }

    private record AdminMessagePayload(Long userId, String message) {

    }

}