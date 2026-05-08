package com.example.booking.api.auth.controller;

import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AuthControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void signup_success() throws Exception {
        SignupRequest request = new SignupRequest(
                "Hong",
                "success@example.com",
                "010-1111-1111",
                "password123"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("success@example.com"))
                .andExpect(jsonPath("$.name").value("Hong"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").exists());

        User saved = userRepository.findByEmail("success@example.com").orElseThrow();
        assertThat(saved.getName()).isEqualTo("Hong");
        assertThat(saved.getPhone()).isEqualTo("010-1111-1111");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    void signup_duplicate_email() throws Exception {
        SignupRequest first = new SignupRequest(
                "First",
                "dup@example.com",
                "010-2222-2222",
                "password123"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        SignupRequest second = new SignupRequest(
                "Second",
                "dup@example.com",
                "010-3333-3333",
                "password456"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_001"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void signup_invalid_input() throws Exception {
        SignupRequest invalid = new SignupRequest(
                "a",
                "not-an-email",
                "010-0000-0000",
                "short"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"))
                .andExpect(jsonPath("$.status").value(400));
    }
}