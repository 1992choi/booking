package com.example.booking.notification.internal;

import com.example.booking.notification.internal.dto.AdminMessageRequest;
import com.example.booking.notification.service.NotificationService;
import com.example.booking.notification.user.domain.UserSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalNotificationController {

    private static final String CHAOS_EMAIL = "circuit@bookit.com";

    private final NotificationService notificationService;
    private final UserSyncRepository userSyncRepository;

    @PostMapping("/api/v1/internal/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendAdminMessage(@RequestBody AdminMessageRequest request) {
        boolean isChaosUser = userSyncRepository.findById(request.userId())
                .map(u -> CHAOS_EMAIL.equals(u.getEmail()))
                .orElse(false);

        if (isChaosUser) {
            log.warn("[CHAOS] 서킷브레이커 테스트 — 강제 오류 발생 userId={}", request.userId());
            throw new RuntimeException("chaos: 알림 서비스 강제 오류");
        }

        notificationService.sendAdminMessage(request.userId(), request.message());
    }

}