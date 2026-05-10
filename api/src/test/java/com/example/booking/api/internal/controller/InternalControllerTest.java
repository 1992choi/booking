package com.example.booking.api.internal.controller;

import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.owner.domain.OwnerType;
import com.example.booking.api.owner.dto.OwnerCreateRequest;
import com.example.booking.api.resource.dto.ResourceCreateRequest;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class InternalControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void getResource_success() throws Exception {
        String token = signupAndLogin("internal1@example.com");
        Long ownerId = registerOwner(token, "My Pension", OwnerType.PENSION);
        Long resourceId = registerResource(token, ownerId, "별채 A", 150000L, 2);

        mockMvc.perform(get("/api/v1/internal/resources/{id}", resourceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resourceId))
                .andExpect(jsonPath("$.name").value("별채 A"))
                .andExpect(jsonPath("$.price").value(150000))
                .andExpect(jsonPath("$.maxCapacity").value(2))
                .andExpect(jsonPath("$.ownerId").value(ownerId));
    }

    @Test
    void getResource_notFound() throws Exception {
        String token = signupAndLogin("internal2@example.com");

        mockMvc.perform(get("/api/v1/internal/resources/{id}", 9999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_005"));
    }

    @Test
    void getUser_success() throws Exception {
        String token = signupAndLogin("internal3@example.com");
        Long userId = getUserId(token, "internal3@example.com");

        mockMvc.perform(get("/api/v1/internal/users/{id}", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("User"))
                .andExpect(jsonPath("$.email").value("internal3@example.com"));
    }

    @Test
    void getUser_notFound() throws Exception {
        String token = signupAndLogin("internal4@example.com");

        mockMvc.perform(get("/api/v1/internal/users/{id}", 9999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void internal_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/internal/resources/{id}", 1L))
                .andExpect(status().isUnauthorized());
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

    private Long registerOwner(String token, String name, OwnerType type) throws Exception {
        OwnerCreateRequest request = new OwnerCreateRequest(name, "02-1234-5678", type);
        String response = mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long registerResource(String token, Long ownerId, String name, Long price, Integer maxCapacity) throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest(name, "설명", price, maxCapacity);
        String response = mockMvc.perform(post("/api/v1/owners/{ownerId}/resources", ownerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long getUserId(String token, String email) throws Exception {
        String response = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}