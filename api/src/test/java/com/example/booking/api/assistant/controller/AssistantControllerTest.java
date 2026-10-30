package com.example.booking.api.assistant.controller;

import com.example.booking.api.assistant.AssistantContext;
import com.example.booking.api.assistant.AssistantService;
import com.example.booking.api.assistant.dto.ChatRequest;
import com.example.booking.api.assistant.dto.ChatTurn;
import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.core.error.BusinessException;
import com.example.booking.api.error.ApiErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AssistantControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @MockitoBean
    AssistantService assistantService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("rl:login:127.0.0.1");
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("질문을 보내면 어시스턴트 응답을 반환한다")
    void chat_success() throws Exception {
        String token = signupAndLogin("assistant1@example.com");
        given(assistantService.chat(eq("예약은 어떻게 취소해?"), any(), any())).willReturn("내 예약 목록에서 취소할 수 있어요.");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("예약은 어떻게 취소해?", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("내 예약 목록에서 취소할 수 있어요."));
    }

    @Test
    @DisplayName("대화 히스토리를 함께 보내면 그대로 전달한다")
    void chat_withHistory_passesThrough() throws Exception {
        String token = signupAndLogin("assistant2@example.com");
        given(assistantService.chat(eq("또 알려줘"), eq(List.of(new ChatTurn("user", "아까 질문"))), any()))
                .willReturn("답변");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("또 알려줘", List.of(new ChatTurn("user", "아까 질문"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("답변"));
    }

    @Test
    @DisplayName("빈 메시지를 보내면 400 반환")
    void chat_blankMessage_returnsBadRequest() throws Exception {
        String token = signupAndLogin("assistant3@example.com");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("호출자의 userId와 JWT를 context로 전달한다")
    void chat_passesCallerContext() throws Exception {
        String token = signupAndLogin("assistant5@example.com");
        ArgumentCaptor<AssistantContext> captor = ArgumentCaptor.forClass(AssistantContext.class);
        given(assistantService.chat(any(), any(), captor.capture())).willReturn("답변");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("질문", null))))
                .andExpect(status().isOk());

        assertThat(captor.getValue().jwt()).isEqualTo(token);
    }

    @Test
    @DisplayName("비인증 요청으로 접근 시 401 반환")
    void chat_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("질문", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("LLM 게이트웨이 서킷 오픈 시 503 반환")
    void chat_assistantUnavailable_returnsServiceUnavailable() throws Exception {
        String token = signupAndLogin("assistant4@example.com");
        given(assistantService.chat(any(), any(), any()))
                .willThrow(new BusinessException(ApiErrorCode.ASSISTANT_UNAVAILABLE));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("질문", null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_005"));
    }

    private String signupAndLogin(String email) throws Exception {
        SignupRequest signup = new SignupRequest("User", email, "010-1234-5678", "password123");
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(email, "password123");
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, TokenResponse.class).accessToken();
    }

}
