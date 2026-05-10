package com.example.booking.notification.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.notification.dto.NotificationResponse;
import com.example.booking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    public List<NotificationResponse> getMyNotifications(@AuthenticationPrincipal AuthPrincipal principal) {
        return notificationService.getMyNotifications(principal.userId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }
}