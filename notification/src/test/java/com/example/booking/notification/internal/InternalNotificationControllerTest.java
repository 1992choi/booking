package com.example.booking.notification.internal;

import com.example.booking.notification.domain.Notification;
import com.example.booking.notification.domain.NotificationRepository;
import com.example.booking.notification.domain.NotificationStatus;
import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.internal.dto.AdminMessageRequest;
import com.example.booking.notification.user.domain.UserSync;
import com.example.booking.notification.user.domain.UserSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class InternalNotificationControllerTest {

    private static final String CHAOS_EMAIL = "circuit@bookit.com";

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserSyncRepository userSyncRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("관리자 메시지 발송 성공 시 204 반환 및 알림 저장")
    void sendAdminMessage_success() throws Exception {
        userSyncRepository.save(UserSync.builder()
                .id(1L).name("홍길동").email("hong@example.com").phone("010-1234-5678").build());

        mockMvc.perform(post("/api/v1/internal/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminMessageRequest(1L, "공지사항"))))
                .andExpect(status().isNoContent());

        Notification saved = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(1L).getFirst();
        assertThat(saved.getType()).isEqualTo(NotificationType.ADMIN_MESSAGE);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getMessage()).isEqualTo("공지사항");
    }

    @Test
    @DisplayName("동기화되지 않은 유저에게 관리자 메시지 발송 시에도 204 반환하되 실패 상태로 저장")
    void sendAdminMessage_userNotSynced_marksFailed() throws Exception {
        mockMvc.perform(post("/api/v1/internal/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminMessageRequest(999L, "공지사항"))))
                .andExpect(status().isNoContent());

        Notification saved = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(999L).getFirst();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("카오스 테스트 유저로 발송 시 500 반환 (서킷브레이커 테스트용 강제 오류)")
    void sendAdminMessage_chaosUser_forcesError() throws Exception {
        userSyncRepository.save(UserSync.builder()
                .id(2L).name("카오스").email(CHAOS_EMAIL).phone("010-0000-0000").build());

        mockMvc.perform(post("/api/v1/internal/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminMessageRequest(2L, "공지사항"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_500"));
    }

}
