package com.example.booking.reservation.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.client.AvailableTimeClient;
import com.example.booking.reservation.client.AvailableTimeSnapshot;
import com.example.booking.reservation.client.ResourceClient;
import com.example.booking.reservation.client.ResourceSnapshot;
import com.example.booking.reservation.dto.CreateReservationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

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
    AvailableTimeClient availableTimeClient;

    @MockitoBean
    JwtVerifier jwtVerifier;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    MockMvc mockMvc;

    static final ResourceSnapshot RESOURCE = new ResourceSnapshot(1L, "별채 A", 150000L, 2, 10L);

    static final AvailableTimeSnapshot SLOT = new AvailableTimeSnapshot(
            1L, 1L,
            LocalDateTime.of(2026, 6, 1, 14, 0),
            LocalDateTime.of(2026, 6, 1, 15, 0),
            "OPEN");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));
        given(resourceClient.fetch(1L)).willReturn(RESOURCE);
        given(resourceClient.fetchAvailableTimes(any())).willReturn(List.of(SLOT));
    }

    @Test
    void create_success() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(1L, List.of(1L), 2);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].resourceName").value("별채 A"))
                .andExpect(jsonPath("$[0].amount").value(150000))
                .andExpect(jsonPath("$[0].headCount").value(2));
    }

    @Test
    void create_capacityExceeded() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(1L, List.of(1L), 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RSV_003"));
    }

    @Test
    void create_timeConflict() throws Exception {
        given(resourceClient.fetchAvailableTimes(List.of(1L))).willReturn(List.of(
                new AvailableTimeSnapshot(1L, 1L,
                        LocalDateTime.of(2026, 6, 1, 14, 0),
                        LocalDateTime.of(2026, 6, 1, 15, 0), "OPEN")));
        given(resourceClient.fetchAvailableTimes(List.of(2L))).willReturn(List.of(
                new AvailableTimeSnapshot(2L, 1L,
                        LocalDateTime.of(2026, 6, 1, 14, 30),
                        LocalDateTime.of(2026, 6, 1, 15, 30), "OPEN")));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(2L), 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV_001"));
    }

    @Test
    void getById_success() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get(0).get("id").asLong();

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
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
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
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get(0).get("id").asLong();

        mockMvc.perform(put("/api/v1/reservations/{id}/cancel", reservationId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_notMyReservation() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get(0).get("id").asLong();

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
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_invalidRequest_emptySlots() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":1,\"availableTimeIds\":[],\"headCount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void create_invalidRequest_headCountZero() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":1,\"availableTimeIds\":[1],\"headCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void create_slotNotOpen() throws Exception {
        given(resourceClient.fetchAvailableTimes(List.of(1L))).willReturn(List.of(
                new com.example.booking.reservation.client.AvailableTimeSnapshot(
                        1L, 1L,
                        LocalDateTime.of(2026, 6, 1, 14, 0),
                        LocalDateTime.of(2026, 6, 1, 15, 0),
                        "BLOCKED")));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV_001"));
    }

    @Test
    void create_slotBelongsToDifferentResource() throws Exception {
        given(resourceClient.fetchAvailableTimes(List.of(1L))).willReturn(List.of(
                new com.example.booking.reservation.client.AvailableTimeSnapshot(
                        1L, 999L,
                        LocalDateTime.of(2026, 6, 1, 14, 0),
                        LocalDateTime.of(2026, 6, 1, 15, 0),
                        "OPEN")));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV_001"));
    }

    @Test
    void getMyReservations_defaultStatusIsPending() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void cancel_alreadyCancelled() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get(0).get("id").asLong();

        mockMvc.perform(put("/api/v1/reservations/{id}/cancel", reservationId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(put("/api/v1/reservations/{id}/cancel", reservationId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}