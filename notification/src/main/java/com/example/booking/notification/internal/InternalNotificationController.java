package com.example.booking.notification.internal;

import com.example.booking.notification.internal.dto.AdminMessageRequest;
import com.example.booking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    @PostMapping("/api/v1/internal/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendAdminMessage(@RequestBody AdminMessageRequest request) {
        notificationService.sendAdminMessage(request.userId(), request.message());
    }

}