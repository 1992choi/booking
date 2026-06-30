package com.example.booking.api.notification;

import com.example.booking.core.error.BusinessException;
import com.example.booking.api.error.ApiErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationClient {

    private final RestClient notificationRestClient;

    @CircuitBreaker(name = "notification", fallbackMethod = "fallback")
    public void sendAdminMessage(Long userId, String message) {
        notificationRestClient.post()
                .uri("/api/v1/internal/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AdminMessagePayload(userId, message))
                .retrieve()
                .toBodilessEntity();
    }

    void fallback(Long userId, String message, Exception e) {
        log.warn("알림 서비스 서킷 오픈 — userId={}, reason={}", userId, e.getMessage());
        throw new BusinessException(ApiErrorCode.NOTIFICATION_UNAVAILABLE);
    }

    private record AdminMessagePayload(Long userId, String message) {

    }

}