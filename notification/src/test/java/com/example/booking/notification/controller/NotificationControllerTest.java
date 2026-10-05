package com.example.booking.notification.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.notification.domain.Notification;
import com.example.booking.notification.domain.NotificationRepository;
import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.service.NotificationService;
import com.example.booking.notification.user.domain.UserSync;
import com.example.booking.notification.user.domain.UserSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class NotificationControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserSyncRepository userSyncRepository;

    @MockitoBean
    JwtVerifier jwtVerifier;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        notificationRepository.deleteAll();
        userSyncRepository.save(UserSync.builder()
                .id(1L).name("홍길동").email("hong@example.com").phone("010-1234-5678").build());

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));
    }

    @Test
    @DisplayName("내 알림 목록 조회 성공 — 최신순 정렬 및 타입 검증")
    void getMyNotifications_success() throws Exception {
        notificationService.send(1L, 10L, NotificationType.CONFIRMED);
        notificationService.send(1L, 11L, NotificationType.CANCELLED);

        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("CANCELLED"))
                .andExpect(jsonPath("$[1].type").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].channel").value("LOG"));
    }

    @Test
    @DisplayName("알림이 없을 때 빈 목록 반환")
    void getMyNotifications_empty() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("비인증 요청으로 알림 조회 시 401 반환")
    void getMyNotifications_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("알림 조회 시 모든 필드 정상 반환")
    void getMyNotifications_verifyFields() throws Exception {
        notificationService.send(1L, 42L, NotificationType.CONFIRMED);

        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reservationId").value(42))
                .andExpect(jsonPath("$[0].type").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].channel").value("LOG"))
                .andExpect(jsonPath("$[0].sentAt").exists());
    }

    @Test
    @DisplayName("다른 유저의 알림은 조회되지 않음")
    void getMyNotifications_onlyMine() throws Exception {
        notificationService.send(1L, 10L, NotificationType.CONFIRMED);

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(2L, Role.USER));

        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", "Bearer other-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("알림 읽음 처리 성공 시 readAt이 채워진다")
    void markRead_success() throws Exception {
        notificationService.send(1L, 10L, NotificationType.CONFIRMED);
        Notification notification = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(1L).get(0);

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notification.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findById(notification.getId()).get().getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 유저의 알림을 읽음 처리하면 403 반환")
    void markRead_otherUsersNotification_forbidden() throws Exception {
        notificationService.send(1L, 10L, NotificationType.CONFIRMED);
        Notification notification = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(1L).get(0);

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(2L, Role.USER));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notification.getId())
                        .header("Authorization", "Bearer other-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 알림을 읽음 처리하면 404 반환")
    void markRead_notFound() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", 9999L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비인증 요청으로 알림 읽음 처리 시 401 반환")
    void markRead_unauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", 1L))
                .andExpect(status().isUnauthorized());
    }
}