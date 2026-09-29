package com.example.booking.notification.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.notification.dto.NotificationResponse;
import com.example.booking.notification.service.NotificationService;
import com.example.booking.notification.sse.SseNotificationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseNotificationRegistry sseNotificationRegistry;
    private final JwtVerifier jwtVerifier;

    @GetMapping("/api/v1/notifications/me")
    public List<NotificationResponse> getMyNotifications(@AuthenticationPrincipal AuthPrincipal principal) {
        return notificationService.getMyNotifications(principal.userId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/notifications/stream")
    public SseEmitter stream(@RequestParam String token) {
        AuthPrincipal principal = jwtVerifier.verify(token);

        return sseNotificationRegistry.register(principal.userId());
    }

}