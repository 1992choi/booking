package com.example.booking.api.admin.controller;

import com.example.booking.api.admin.client.ReservationClient;
import com.example.booking.api.admin.dto.AdminCalendarEntry;
import com.example.booking.api.admin.dto.AdminReservationResponse;
import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.merchant.domain.MerchantType;
import com.example.booking.api.merchant.dto.MerchantCreateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AdminControllerTest {

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
    void getCalendar_merchantSuccess() throws Exception {
        given(reservationClient.getCalendar(anyInt(), anyInt(), anyString()))
                .willReturn(Map.of(
                        LocalDate.of(2026, 7, 1),
                        List.of(new AdminCalendarEntry(1L, "별채 A", "14:00", "15:00", "CONFIRMED"))
                ));

        String token = signupAsMerchant("admin_cal1@example.com");

        mockMvc.perform(get("/api/v1/admin/reservations/calendar")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['2026-07-01'][0].resourceName").value("별채 A"));
    }

    @Test
    void getCalendar_userForbidden() throws Exception {
        String token = signupAndLogin("admin_cal2@example.com");

        mockMvc.perform(get("/api/v1/admin/reservations/calendar")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCalendar_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reservations/calendar")
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirm_merchantSuccess() throws Exception {
        given(reservationClient.confirm(anyLong(), anyString()))
                .willReturn(new AdminReservationResponse(1L, "CONFIRMED", "별채 A", null, null, 1, 150000L));

        String token = signupAsMerchant("admin1@example.com");

        mockMvc.perform(put("/api/v1/admin/reservations/{id}/confirm", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancel_merchantSuccess() throws Exception {
        given(reservationClient.cancel(anyLong(), anyString()))
                .willReturn(new AdminReservationResponse(1L, "CANCELLED", "별채 A", null, null, 1, 150000L));

        String token = signupAsMerchant("admin2@example.com");

        mockMvc.perform(put("/api/v1/admin/reservations/{id}/cancel", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void confirm_userForbidden() throws Exception {
        String token = signupAndLogin("admin3@example.com");

        mockMvc.perform(put("/api/v1/admin/reservations/{id}/confirm", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_userForbidden() throws Exception {
        String token = signupAndLogin("admin4@example.com");

        mockMvc.perform(put("/api/v1/admin/reservations/{id}/cancel", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirm_unauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reservations/{id}/confirm", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_unauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reservations/{id}/cancel", 1L))
                .andExpect(status().isUnauthorized());
    }

    private String signupAsMerchant(String email) throws Exception {
        String token = signupAndLogin(email);

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("테스트 업체", "02-1234-5678", MerchantType.PENSION))))
                .andExpect(status().isCreated());

        // Merchant 등록 후 재로그인하여 MERCHANT 역할이 담긴 토큰 획득
        return login(email);
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
}