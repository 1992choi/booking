package com.example.booking.reservation.admin.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.domain.Reservation;
import com.example.booking.reservation.domain.ReservationRepository;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.resource.domain.AvailableTime;
import com.example.booking.reservation.resource.domain.AvailableTimeRepository;
import com.example.booking.reservation.resource.domain.AvailableTimeStatus;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.domain.ResourceRepository;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    AvailableTimeRepository availableTimeRepository;

    @MockitoBean
    JwtVerifier jwtVerifier;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    MockMvc mockMvc;

    static final AtomicLong userIdSeq = new AtomicLong(400L);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(userIdSeq.incrementAndGet(), Role.MERCHANT));
    }

    @Test
    void getCalendar_merchantSuccess() throws Exception {
        Resource resource = resourceRepository.save(Resource.builder()
                .merchantId(1L).name("별채 A").description("").price(150000L).maxCapacity(2).build());
        AvailableTime slot = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(LocalDateTime.of(2026, 7, 1, 14, 0))
                .endTime(LocalDateTime.of(2026, 7, 1, 15, 0))
                .status(AvailableTimeStatus.OPEN)
                .build());
        reservationRepository.save(Reservation.builder()
                .availableTimeId(slot.getId())
                .userId(1L)
                .resourceId(resource.getId())
                .resourceName("별채 A")
                .startTime(LocalDateTime.of(2026, 7, 1, 14, 0))
                .endTime(LocalDateTime.of(2026, 7, 1, 15, 0))
                .status(ReservationStatus.CONFIRMED)
                .headCount(1)
                .amount(150000L)
                .build());

        mockMvc.perform(get("/api/v1/admin/reservations/calendar")
                        .header("Authorization", "Bearer token")
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['2026-07-01'][0].resourceName").value("별채 A"));
    }

    @Test
    void getCalendar_userForbidden() throws Exception {
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));

        mockMvc.perform(get("/api/v1/admin/reservations/calendar")
                        .header("Authorization", "Bearer token")
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
        Reservation reservation = createReservation(LocalDateTime.of(2026, 10, 1, 10, 0));

        mockMvc.perform(put("/api/v1/admin/reservations/{id}/confirm", reservation.getId())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancel_merchantSuccess() throws Exception {
        Reservation reservation = createReservation(LocalDateTime.of(2026, 10, 2, 10, 0));

        mockMvc.perform(put("/api/v1/admin/reservations/{id}/cancel", reservation.getId())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void confirm_userForbidden() throws Exception {
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));

        mockMvc.perform(put("/api/v1/admin/reservations/{id}/confirm", 1L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_userForbidden() throws Exception {
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));

        mockMvc.perform(put("/api/v1/admin/reservations/{id}/cancel", 1L)
                        .header("Authorization", "Bearer token"))
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

    private Reservation createReservation(LocalDateTime start) {
        Resource resource = resourceRepository.save(Resource.builder()
                .merchantId(1L).name("별채 A").description("").price(150000L).maxCapacity(2).build());
        AvailableTime slot = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(start)
                .endTime(start.plusHours(1))
                .status(AvailableTimeStatus.OPEN)
                .build());
        return reservationRepository.save(Reservation.builder()
                .availableTimeId(slot.getId())
                .userId(1L)
                .resourceId(resource.getId())
                .resourceName("별채 A")
                .startTime(start)
                .endTime(start.plusHours(1))
                .status(ReservationStatus.PENDING)
                .headCount(1)
                .amount(150000L)
                .build());
    }
}
