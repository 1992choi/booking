package com.example.booking.api.auth.controller;

import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.core.audit.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AuthAuditTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    MongoTemplate mongoTemplate;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("rl:login:127.0.0.1");
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("로그인 성공 시 LOGIN 감사 로그가 MongoDB에 기록된다")
    void login_recordsAuditLog() throws Exception {
        String email = "audit-login-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("Audit", email, "010-0000-0000", "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk());

        List<AuditLog> logs = mongoTemplate.find(
                Query.query(Criteria.where("action").is("LOGIN").and("detail.email").is(email)),
                AuditLog.class, "audit_logs");

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).action()).isEqualTo("LOGIN");
    }

    @Test
    @DisplayName("로그인 실패 시에는 LOGIN 감사 로그가 기록되지 않는다")
    void login_failure_doesNotRecordAuditLog() throws Exception {
        String email = "audit-login-fail-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("Audit", email, "010-0000-0000", "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
                .andExpect(status().isUnauthorized());

        List<AuditLog> logs = mongoTemplate.find(
                Query.query(Criteria.where("action").is("LOGIN").and("detail.email").is(email)),
                AuditLog.class, "audit_logs");

        assertThat(logs).isEmpty();
    }

}
