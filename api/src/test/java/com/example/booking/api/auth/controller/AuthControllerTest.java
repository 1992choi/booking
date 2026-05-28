package com.example.booking.api.auth.controller;

import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.RefreshRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("회원가입 성공 시 유저 정보 반환 및 DB 저장")
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
    @DisplayName("중복 이메일로 회원가입 시 409 반환")
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
    @DisplayName("잘못된 입력(이름 짧음, 이메일 형식 오류, 비밀번호 짧음)으로 회원가입 시 400 반환")
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

    @Test
    @DisplayName("로그인 성공 시 액세스 토큰 반환")
    void login_success() throws Exception {
        SignupRequest signup = new SignupRequest(
                "Login",
                "login@example.com",
                "010-1234-5678",
                "password123"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("login@example.com", "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 401 반환")
    void login_invalid_credentials() throws Exception {
        SignupRequest signup = new SignupRequest(
                "WrongPw",
                "wrongpw@example.com",
                "010-1234-5678",
                "password123"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("wrongpw@example.com", "wrongpassword");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_002"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시 401 반환")
    void login_user_not_found() throws Exception {
        LoginRequest login = new LoginRequest("nobody@example.com", "anypassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_002"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("로그인 성공 시 리프레시 토큰 및 만료 시간 포함 반환")
    void login_returnsRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("User", "refresh1@example.com", "010-0000-0000", "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("refresh1@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("리프레시 토큰으로 액세스 토큰 갱신 성공")
    void refresh_success() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("User", "refresh2@example.com", "010-0000-0000", "password123"))))
                .andExpect(status().isCreated());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("refresh2@example.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readValue(loginBody, TokenResponse.class).refreshToken();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 갱신 시 401 반환")
    void refresh_invalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("invalid.token.here"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("액세스 토큰을 갱신 엔드포인트에 사용하면 401 반환")
    void refresh_accessTokenRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("User", "refresh3@example.com", "010-0000-0000", "password123"))))
                .andExpect(status().isCreated());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("refresh3@example.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readValue(loginBody, TokenResponse.class).accessToken();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(accessToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("이름이 빈값이면 회원가입 시 400 반환")
    void signup_blankName() throws Exception {
        SignupRequest request = new SignupRequest("", "blank@example.com", "010-1234-5678", "password123");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 회원가입 시 400 반환")
    void signup_passwordTooShort() throws Exception {
        SignupRequest request = new SignupRequest("Hong", "short@example.com", "010-1234-5678", "1234567");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("빈 요청 바디로 로그인 시 400 반환")
    void login_emptyBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }
}