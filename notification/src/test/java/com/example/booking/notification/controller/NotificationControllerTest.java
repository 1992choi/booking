package com.example.booking.notification.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.notification.domain.NotificationRepository;
import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.service.NotificationService;
import com.example.booking.notification.user.domain.UserSync;
import com.example.booking.notification.user.domain.UserSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void getMyNotifications_empty() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMyNotifications_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
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
    void getMyNotifications_onlyMine() throws Exception {
        notificationService.send(1L, 10L, NotificationType.CONFIRMED);

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(2L, Role.USER));

        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", "Bearer other-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
