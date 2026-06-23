package com.example.booking.api.user.controller;

import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import com.example.booking.api.user.dto.UserUpdateRequest;
import com.example.booking.core.auth.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class UserControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("rl:login:127.0.0.1");
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMe_success() throws Exception {
        SignupRequest signup = new SignupRequest(
                "Me",
                "me@example.com",
                "010-1234-5678",
                "password123"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("me@example.com", "password123");
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        TokenResponse token = objectMapper.readValue(loginResponse, TokenResponse.class);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.name").value("Me"))
                .andExpect(jsonPath("$.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("토큰 없이 내 정보 조회 시 401 반환")
    void getMe_no_token() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 내 정보 조회 시 401 반환")
    void getMe_invalid_token() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("내 정보 수정 성공")
    void updateMe_success() throws Exception {
        String token = signupAndLogin("update@example.com", "010-1111-1111");

        UserUpdateRequest update = new UserUpdateRequest("Updated", "010-9999-9999");
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.phone").value("010-9999-9999"))
                .andExpect(jsonPath("$.email").value("update@example.com"));
    }

    @Test
    @DisplayName("토큰 없이 내 정보 수정 시 401 반환")
    void updateMe_no_token() throws Exception {
        UserUpdateRequest update = new UserUpdateRequest("Updated", "010-9999-9999");
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이름이 빈값이면 내 정보 수정 시 400 반환")
    void updateMe_invalidInput_blankName() throws Exception {
        String token = signupAndLogin("blankname@example.com", "010-3333-3333");

        UserUpdateRequest update = new UserUpdateRequest("", "010-9999-9999");
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("전화번호가 빈값이면 내 정보 수정 시 400 반환")
    void updateMe_invalidInput_blankPhone() throws Exception {
        String token = signupAndLogin("blankphone@example.com", "010-4444-4444");

        UserUpdateRequest update = new UserUpdateRequest("Valid Name", "");
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("회원 탈퇴 성공 후 조회 시 404 반환")
    void deleteMe_success() throws Exception {
        String token = signupAndLogin("delete@example.com", "010-2222-2222");

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰 없이 회원 탈퇴 시 401 반환")
    void deleteMe_no_token() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자가 필터 없이 전체 유저 조회 성공")
    void getUsers_admin_noFilter_success() throws Exception {
        String adminToken = createAdminAndLogin("admin1@example.com");
        signupAndLogin("user1@example.com", "010-1111-0001");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("관리자가 유저 타입으로 필터링해서 조회 성공")
    void getUsers_admin_withRoleFilter_success() throws Exception {
        String adminToken = createAdminAndLogin("admin2@example.com");
        signupAndLogin("user2@example.com", "010-1111-0002");

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "USER")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].role", everyItem(is("USER"))));
    }

    @Test
    @DisplayName("일반 유저가 관리자 API 접근 시 403 반환")
    void getUsers_notAdmin_forbidden() throws Exception {
        String userToken = signupAndLogin("regular@example.com", "010-1111-0003");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("토큰 없이 관리자 API 접근 시 401 반환")
    void getUsers_noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 유저 타입으로 필터링 시 400 반환")
    void getUsers_invalidRole_badRequest() throws Exception {
        String adminToken = createAdminAndLogin("admin3@example.com");

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "INVALID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    private String createAdminAndLogin(String email) throws Exception {
        User admin = User.builder()
                .name("Admin")
                .email(email)
                .phone("010-0000-0000")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        LoginRequest login = new LoginRequest(email, "password123");
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(response, TokenResponse.class).accessToken();
    }

    private String signupAndLogin(String email, String phone) throws Exception {
        SignupRequest signup = new SignupRequest("User", email, phone, "password123");
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