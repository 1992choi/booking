package com.example.booking.reservation.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.client.AvailableTimeSnapshot;
import com.example.booking.reservation.client.ResourceClient;
import com.example.booking.reservation.client.ResourceSnapshot;
import com.example.booking.reservation.dto.CreateReservationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class InternalReservationControllerTest {

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

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

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
    void getAll_withoutStatusFilter() throws Exception {
        createReservation(LocalDateTime.of(2026, 7, 1, 14, 0), LocalDateTime.of(2026, 7, 1, 15, 0));
        createReservation(LocalDateTime.of(2026, 7, 1, 16, 0), LocalDateTime.of(2026, 7, 1, 17, 0));

        mockMvc.perform(get("/api/v1/internal/reservations")
                        .header("Authorization", "Bearer test-token")
                        .param("date", "2026-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getAll_withStatusFilter() throws Exception {
        createReservation(LocalDateTime.of(2026, 7, 2, 10, 0), LocalDateTime.of(2026, 7, 2, 11, 0));

        mockMvc.perform(get("/api/v1/internal/reservations")
                        .header("Authorization", "Bearer test-token")
                        .param("date", "2026-07-02")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void getAll_statusFilterNoMatch() throws Exception {
        createReservation(LocalDateTime.of(2026, 7, 3, 10, 0), LocalDateTime.of(2026, 7, 3, 11, 0));

        mockMvc.perform(get("/api/v1/internal/reservations")
                        .header("Authorization", "Bearer test-token")
                        .param("date", "2026-07-03")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getAll_otherDateExcluded() throws Exception {
        createReservation(LocalDateTime.of(2026, 7, 4, 10, 0), LocalDateTime.of(2026, 7, 4, 11, 0));

        mockMvc.perform(get("/api/v1/internal/reservations")
                        .header("Authorization", "Bearer test-token")
                        .param("date", "2026-07-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getAll_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/internal/reservations")
                        .param("date", "2026-07-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCalendar_groupedByDate() throws Exception {
        createReservation(LocalDateTime.of(2026, 8, 1, 10, 0), LocalDateTime.of(2026, 8, 1, 11, 0));
        createReservation(LocalDateTime.of(2026, 8, 1, 14, 0), LocalDateTime.of(2026, 8, 1, 15, 0));
        createReservation(LocalDateTime.of(2026, 8, 3, 10, 0), LocalDateTime.of(2026, 8, 3, 11, 0));

        mockMvc.perform(get("/api/v1/internal/reservations/calendar")
                        .header("Authorization", "Bearer test-token")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['2026-08-01'].length()").value(2))
                .andExpect(jsonPath("$['2026-08-03'].length()").value(1))
                .andExpect(jsonPath("$['2026-08-01'][0].startTime").value("10:00"))
                .andExpect(jsonPath("$['2026-08-01'][0].resourceName").value("별채 A"));
    }

    @Test
    void getCalendar_otherMonthExcluded() throws Exception {
        createReservation(LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 1, 11, 0));

        mockMvc.perform(get("/api/v1/internal/reservations/calendar")
                        .header("Authorization", "Bearer test-token")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getCalendar_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/internal/reservations/calendar")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_success() throws Exception {
        String response = createReservationAndGetBody(
                LocalDateTime.of(2026, 10, 1, 10, 0), LocalDateTime.of(2026, 10, 1, 11, 0));
        Long reservationId = objectMapper.readTree(response).get(0).get("id").asLong();

        mockMvc.perform(get("/api/v1/internal/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.resourceName").value("별채 A"));
    }

    @Test
    void getById_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/internal/reservations/{id}", 9999L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RSV_004"));
    }

    @Test
    void getByResources_withoutStatusFilter() throws Exception {
        createReservation(LocalDateTime.of(2026, 11, 1, 10, 0), LocalDateTime.of(2026, 11, 1, 11, 0));
        createReservation(LocalDateTime.of(2026, 11, 1, 14, 0), LocalDateTime.of(2026, 11, 1, 15, 0));

        mockMvc.perform(get("/api/v1/internal/reservations/by-resources")
                        .header("Authorization", "Bearer test-token")
                        .param("resourceIds", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getByResources_withStatusFilter() throws Exception {
        createReservation(LocalDateTime.of(2026, 11, 2, 10, 0), LocalDateTime.of(2026, 11, 2, 11, 0));

        mockMvc.perform(get("/api/v1/internal/reservations/by-resources")
                        .header("Authorization", "Bearer test-token")
                        .param("resourceIds", "1")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void getByResources_statusFilterNoMatch() throws Exception {
        createReservation(LocalDateTime.of(2026, 11, 3, 10, 0), LocalDateTime.of(2026, 11, 3, 11, 0));

        mockMvc.perform(get("/api/v1/internal/reservations/by-resources")
                        .header("Authorization", "Bearer test-token")
                        .param("resourceIds", "1")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getByResources_resourceNotInList_excluded() throws Exception {
        createReservation(LocalDateTime.of(2026, 11, 4, 10, 0), LocalDateTime.of(2026, 11, 4, 11, 0));

        mockMvc.perform(get("/api/v1/internal/reservations/by-resources")
                        .header("Authorization", "Bearer test-token")
                        .param("resourceIds", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getByResources_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/internal/reservations/by-resources")
                        .param("resourceIds", "1"))
                .andExpect(status().isUnauthorized());
    }

    private void createReservation(LocalDateTime start, LocalDateTime end) throws Exception {
        createReservationAndGetBody(start, end);
    }

    private String createReservationAndGetBody(LocalDateTime start, LocalDateTime end) throws Exception {
        given(resourceClient.fetchAvailableTimes(any()))
                .willReturn(List.of(new AvailableTimeSnapshot(1L, 1L, start, end, "OPEN")));

        return mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(1L, List.of(1L), 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}