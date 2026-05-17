package com.example.booking.api.resource.controller;

import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.merchant.domain.MerchantType;
import com.example.booking.api.merchant.dto.MerchantCreateRequest;
import com.example.booking.api.resource.dto.AvailableTimeCreateRequest;
import com.example.booking.api.resource.dto.ResourceCreateRequest;
import com.example.booking.api.resource.dto.ResourceUpdateRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        Long merchantId = registerMerchant(token, "Test Pension", MerchantType.PENSION);

        ResourceCreateRequest request = new ResourceCreateRequest(
                "별채 A", "2인실 독채", 150000L, 2);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchantId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.merchantId").value(merchantId))
                .andExpect(jsonPath("$.name").value("별채 A"))
                .andExpect(jsonPath("$.price").value(150000))
                .andExpect(jsonPath("$.maxCapacity").value(2));
    }

    @Test
    void registerResource_merchantNotFound() throws Exception {
        String token = signupAndLogin("resource2@example.com");

        ResourceCreateRequest request = new ResourceCreateRequest(
                "별채 A", "설명", 100000L, 2);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", 9999L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_003"));
    }

    @Test
    void registerResource_unauthorized() throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest(
                "별채 A", "설명", 100000L, 2);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addAvailableTime_success() throws Exception {
        String token = signupAndLogin("resource3@example.com");
        Long merchantId = registerMerchant(token, "My Pension", MerchantType.PENSION);
        Long resourceId = registerResource(token, merchantId, "별채 B", 100000L);

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
                .andExpect(jsonPath("$.code").value("API_004"));
    }

    @Test
    void getAvailableTimes_success() throws Exception {
        String token = signupAndLogin("resource5@example.com");
        Long merchantId = registerMerchant(token, "Class Center", MerchantType.CLASS);
        Long resourceId = registerResource(token, merchantId, "강의실 A", 50000L);

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
                .andExpect(jsonPath("$.code").value("API_004"));
    }

    @Test
    void updateResource_success() throws Exception {
        String token = signupAndLogin("resource_upd1@example.com");
        Long merchantId = registerMerchant(token, "Update Pension", MerchantType.PENSION);
        Long resourceId = registerResource(token, merchantId, "별채 A", 150000L);

        ResourceUpdateRequest update = new ResourceUpdateRequest("별채 B", "리모델링 완료", 200000L, 4);
        mockMvc.perform(put("/api/v1/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("별채 B"))
                .andExpect(jsonPath("$.price").value(200000))
                .andExpect(jsonPath("$.maxCapacity").value(4));
    }

    @Test
    void updateResource_forbidden() throws Exception {
        String merchantToken = signupAndLogin("resource_upd2@example.com");
        Long merchantId = registerMerchant(merchantToken, "Pension A", MerchantType.PENSION);
        Long resourceId = registerResource(merchantToken, merchantId, "별채 A", 150000L);

        String otherToken = signupAndLogin("resource_upd3@example.com");
        registerMerchant(otherToken, "Pension B", MerchantType.PENSION);

        ResourceUpdateRequest update = new ResourceUpdateRequest("해킹 시도", null, 1L, 1);
        mockMvc.perform(put("/api/v1/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteResource_success() throws Exception {
        String token = signupAndLogin("resource_del1@example.com");
        Long merchantId = registerMerchant(token, "Delete Pension", MerchantType.PENSION);
        Long resourceId = registerResource(token, merchantId, "별채 A", 150000L);

        mockMvc.perform(delete("/api/v1/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteResource_forbidden() throws Exception {
        String merchantToken = signupAndLogin("resource_del2@example.com");
        Long merchantId = registerMerchant(merchantToken, "Pension A", MerchantType.PENSION);
        Long resourceId = registerResource(merchantToken, merchantId, "별채 A", 150000L);

        String otherToken = signupAndLogin("resource_del3@example.com");
        registerMerchant(otherToken, "Pension B", MerchantType.PENSION);

        mockMvc.perform(delete("/api/v1/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
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

    private Long registerMerchant(String token, String name, MerchantType type) throws Exception {
        MerchantCreateRequest request = new MerchantCreateRequest(name, "02-1234-5678", type);
        String response = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long registerResource(String token, Long merchantId, String name, Long price) throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest(name, "설명", price, 4);
        String response = mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchantId)
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