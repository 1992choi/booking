package com.example.booking.reservation.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.client.ResourceClient;
import com.example.booking.reservation.client.ResourceSnapshot;
import com.example.booking.reservation.dto.CreateReservationRequest;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ReservationControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ResourceClient resourceClient;

    @MockitoBean
    JwtVerifier jwtVerifier;

    MockMvc mockMvc;

    static final ResourceSnapshot RESOURCE = new ResourceSnapshot(1L, "별채 A", 150000L, 2, 10L);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));
        given(resourceClient.fetch(1L)).willReturn(RESOURCE);
    }

    @Test
    void create_success() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                1L,
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0),
                2);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.resourceName").value("별채 A"))
                .andExpect(jsonPath("$.amount").value(150000))
                .andExpect(jsonPath("$.headCount").value(2));
    }

    @Test
    void create_capacityExceeded() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(
                1L,
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0),
                3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RSV_003"));
    }

    @Test
    void create_timeConflict() throws Exception {
        CreateReservationRequest first = new CreateReservationRequest(
                1L,
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0),
                1);
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        CreateReservationRequest overlap = new CreateReservationRequest(
                1L,
                LocalDateTime.of(2026, 6, 1, 14, 30),
                LocalDateTime.of(2026, 6, 1, 15, 30),
                1);
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlap)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV_001"));
    }

    @Test
    void getById_success() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                1L,
                                LocalDateTime.of(2026, 6, 2, 10, 0),
                                LocalDateTime.of(2026, 6, 2, 11, 0),
                                1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.resourceName").value("별채 A"));
    }

    @Test
    void getById_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/{id}", 9999L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RSV_004"));
    }

    @Test
    void getMyReservations_success() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                1L,
                                LocalDateTime.of(2026, 6, 3, 10, 0),
                                LocalDateTime.of(2026, 6, 3, 11, 0),
                                1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer test-token")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.resourceName == '별채 A')]").exists());
    }

    @Test
    void cancel_success() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                1L,
                                LocalDateTime.of(2026, 6, 4, 10, 0),
                                LocalDateTime.of(2026, 6, 4, 11, 0),
                                1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(put("/api/v1/reservations/{id}/cancel", reservationId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_notOwner() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                1L,
                                LocalDateTime.of(2026, 6, 5, 10, 0),
                                LocalDateTime.of(2026, 6, 5, 11, 0),
                                1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get("id").asLong();

        // 다른 유저로 취소 시도
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(999L, Role.USER));

        mockMvc.perform(put("/api/v1/reservations/{id}/cancel", reservationId)
                        .header("Authorization", "Bearer other-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RSV_005"));
    }

    @Test
    void create_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                1L,
                                LocalDateTime.of(2026, 6, 1, 14, 0),
                                LocalDateTime.of(2026, 6, 1, 15, 0),
                                1))))
                .andExpect(status().isUnauthorized());
    }
}