package com.example.booking.api.merchant.controller;

import com.example.booking.api.admin.client.ReservationClient;
import com.example.booking.api.admin.dto.AdminReservationPageResponse;
import com.example.booking.api.admin.dto.AdminReservationResponse;
import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.merchant.domain.MerchantType;
import com.example.booking.api.merchant.dto.MerchantCreateRequest;
import com.example.booking.api.resource.dto.ResourceCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class MerchantReservationControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ReservationClient reservationClient;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void getMerchantReservations_success() throws Exception {
        String token = signupAndLogin("res_merchant1@example.com");
        Long merchantId = registerMerchant(token, "Sunset Pension");
        addResource(token, merchantId, "별채 A");

        Long userId = getUserId(token);

        given(reservationClient.getByMerchant(any(), nullable(String.class), anyInt(), anyInt(), anyString()))
                .willReturn(new AdminReservationPageResponse(
                        List.of(new AdminReservationResponse(1L, "PENDING", "별채 A", null, null, 2, 100000L, userId, null)),
                        0, 10, 1L, 1));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].resourceName").value("별채 A"))
                .andExpect(jsonPath("$.content[0].userName").value("테스트"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getMerchantReservations_withStatusFilter() throws Exception {
        String token = signupAndLogin("res_merchant2@example.com");
        Long merchantId = registerMerchant(token, "Beach House");
        addResource(token, merchantId, "오션뷰");

        given(reservationClient.getByMerchant(any(), nullable(String.class), anyInt(), anyInt(), anyString()))
                .willReturn(new AdminReservationPageResponse(List.of(), 0, 10, 0L, 0));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchantId)
                        .header("Authorization", "Bearer " + token)
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getMerchantReservations_emptyResources_returnsEmptyWithoutCallingClient() throws Exception {
        String token = signupAndLogin("res_merchant3@example.com");
        Long merchantId = registerMerchant(token, "No Resource Merchant");

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(reservationClient, never()).getByMerchant(any(), nullable(String.class), anyInt(), anyInt(), anyString());
    }

    @Test
    void getMerchantReservations_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void getMerchantReservations_forbidden_otherUser() throws Exception {
        String ownerToken = signupAndLogin("res_merchant4@example.com");
        Long merchantId = registerMerchant(ownerToken, "Owned Merchant");

        String otherToken = signupAndLogin("res_merchant5@example.com");

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchantId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void getMerchantReservations_merchantNotFound() throws Exception {
        String token = signupAndLogin("res_merchant6@example.com");

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", 9999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_003"));
    }

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("테스트", email, "010-0000-0000", "password123"))))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, TokenResponse.class).accessToken();
    }

    private Long registerMerchant(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest(name, "02-1234-5678", MerchantType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void addResource(String token, Long merchantId, String resourceName) throws Exception {
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchantId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResourceCreateRequest(resourceName, "설명", 100000L, 2))))
                .andExpect(status().isCreated());
    }

    private Long getUserId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
