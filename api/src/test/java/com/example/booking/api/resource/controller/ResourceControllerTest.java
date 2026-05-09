package com.example.booking.api.resource.controller;

import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.owner.domain.OwnerType;
import com.example.booking.api.owner.dto.OwnerCreateRequest;
import com.example.booking.api.resource.dto.AvailableTimeCreateRequest;
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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ResourceControllerTest {

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
    void registerResource_success() throws Exception {
        String token = signupAndLogin("resource1@example.com");
        Long ownerId = registerOwner(token, "Test Pension", OwnerType.PENSION);

        ResourceCreateRequest request = new ResourceCreateRequest(
                "별채 A", "2인실 독채", 150000L, 2);

        mockMvc.perform(post("/api/v1/owners/{ownerId}/resources", ownerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ownerId").value(ownerId))
                .andExpect(jsonPath("$.name").value("별채 A"))
                .andExpect(jsonPath("$.price").value(150000))
                .andExpect(jsonPath("$.maxCapacity").value(2));
    }

    @Test
    void registerResource_ownerNotFound() throws Exception {
        String token = signupAndLogin("resource2@example.com");

        ResourceCreateRequest request = new ResourceCreateRequest(
                "별채 A", "설명", 100000L, 2);

        mockMvc.perform(post("/api/v1/owners/{ownerId}/resources", 9999L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_004"));
    }

    @Test
    void registerResource_unauthorized() throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest(
                "별채 A", "설명", 100000L, 2);

        mockMvc.perform(post("/api/v1/owners/{ownerId}/resources", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addAvailableTime_success() throws Exception {
        String token = signupAndLogin("resource3@example.com");
        Long ownerId = registerOwner(token, "My Pension", OwnerType.PENSION);
        Long resourceId = registerResource(token, ownerId, "별채 B", 100000L);

        AvailableTimeCreateRequest request = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0));

        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.startTime").value("2026-06-01T14:00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-06-01T15:00:00"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void addAvailableTime_resourceNotFound() throws Exception {
        String token = signupAndLogin("resource4@example.com");

        AvailableTimeCreateRequest request = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0));

        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", 9999L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_005"));
    }

    @Test
    void getAvailableTimes_success() throws Exception {
        String token = signupAndLogin("resource5@example.com");
        Long ownerId = registerOwner(token, "Class Center", OwnerType.CLASS);
        Long resourceId = registerResource(token, ownerId, "강의실 A", 50000L);

        AvailableTimeCreateRequest t1 = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 6, 10, 10, 0),
                LocalDateTime.of(2026, 6, 10, 11, 0));
        AvailableTimeCreateRequest t2 = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 6, 10, 14, 0),
                LocalDateTime.of(2026, 6, 10, 15, 0));

        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t2)))
                .andExpect(status().isCreated());

        // GET without auth (permitAll)
        mockMvc.perform(get("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .param("date", "2026-06-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void getAvailableTimes_resourceNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/resources/{resourceId}/available-times", 9999L)
                        .param("date", "2026-06-10"))
                .andExpect(status().isNotFound())
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
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, TokenResponse.class).accessToken();
    }

    private Long registerOwner(String token, String name, OwnerType type) throws Exception {
        OwnerCreateRequest request = new OwnerCreateRequest(name, "02-1234-5678", type);
        String response = mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long registerResource(String token, Long ownerId, String name, Long price) throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest(name, "설명", price, 4);
        String response = mockMvc.perform(post("/api/v1/owners/{ownerId}/resources", ownerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}