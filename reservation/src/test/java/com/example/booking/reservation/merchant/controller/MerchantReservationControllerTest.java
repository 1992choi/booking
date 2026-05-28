package com.example.booking.reservation.merchant.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.domain.Reservation;
import com.example.booking.reservation.domain.ReservationRepository;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantRepository;
import com.example.booking.reservation.merchant.domain.MerchantType;
import com.example.booking.reservation.resource.domain.AvailableTime;
import com.example.booking.reservation.resource.domain.AvailableTimeRepository;
import com.example.booking.reservation.resource.domain.AvailableTimeStatus;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.domain.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    @Autowired
    MerchantRepository merchantRepository;

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    AvailableTimeRepository availableTimeRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @MockitoBean
    JwtVerifier jwtVerifier;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    MockMvc mockMvc;

    static final AtomicLong userIdSeq = new AtomicLong(300L);
    long userId;
    Merchant merchant;
    Resource resource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        userId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(userId, Role.USER));

        merchant = merchantRepository.save(Merchant.builder()
                .userId(userId)
                .name("Test Merchant")
                .phone("02-0000-0000")
                .type(MerchantType.PENSION)
                .build());

        resource = resourceRepository.save(Resource.builder()
                .merchantId(merchant.getId())
                .name("별채 A")
                .description("2인실")
                .price(150000L)
                .maxCapacity(4)
                .build());
    }

    @Test
    @DisplayName("리소스가 없는 Merchant의 예약 목록 조회 시 빈 목록 반환")
    void getMerchantReservations_emptyResources_returnsEmptyWithoutReservations() throws Exception {
        Merchant emptyMerchant = merchantRepository.save(Merchant.builder()
                .userId(userId)
                .name("Empty Merchant")
                .phone("02-0000-0001")
                .type(MerchantType.CLASS)
                .build());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", emptyMerchant.getId())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("예약이 없는 Merchant의 예약 목록 조회 시 빈 목록 반환")
    void getMerchantReservations_noReservations() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("비인증 요청으로 Merchant 예약 목록 조회 시 401 반환")
    void getMerchantReservations_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("다른 유저가 Merchant 예약 목록 조회 시 403 반환")
    void getMerchantReservations_forbidden_otherUser() throws Exception {
        long otherUserId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId())
                        .header("Authorization", "Bearer otherToken"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("존재하지 않는 Merchant의 예약 목록 조회 시 404 반환")
    void getMerchantReservations_merchantNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", 9999L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_006"));
    }

    @Test
    @DisplayName("Merchant 예약 목록 조회 성공 — 예약 정보 검증")
    void getMerchantReservations_withReservations() throws Exception {
        AvailableTime slot = createSlot(
                LocalDateTime.of(2026, 8, 1, 14, 0), LocalDateTime.of(2026, 8, 1, 15, 0));
        reservationRepository.save(Reservation.builder()
                .availableTimeId(slot.getId())
                .userId(userId)
                .resourceId(resource.getId())
                .resourceName(resource.getName())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(ReservationStatus.PENDING)
                .headCount(2)
                .amount(resource.getPrice())
                .build());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].resourceName").value("별채 A"))
                .andExpect(jsonPath("$.content[0].headCount").value(2))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("Merchant 예약 목록 상태 필터 조회 성공")
    void getMerchantReservations_statusFilter() throws Exception {
        AvailableTime slot1 = createSlot(
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 1, 11, 0));
        AvailableTime slot2 = createSlot(
                LocalDateTime.of(2026, 9, 2, 10, 0), LocalDateTime.of(2026, 9, 2, 11, 0));

        reservationRepository.save(Reservation.builder()
                .availableTimeId(slot1.getId())
                .userId(userId).resourceId(resource.getId()).resourceName(resource.getName())
                .startTime(slot1.getStartTime()).endTime(slot1.getEndTime())
                .status(ReservationStatus.PENDING).headCount(1).amount(150000L)
                .build());
        reservationRepository.save(Reservation.builder()
                .availableTimeId(slot2.getId())
                .userId(userId).resourceId(resource.getId()).resourceName(resource.getName())
                .startTime(slot2.getStartTime()).endTime(slot2.getEndTime())
                .status(ReservationStatus.CONFIRMED).headCount(1).amount(150000L)
                .build());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId())
                        .header("Authorization", "Bearer token")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId())
                        .header("Authorization", "Bearer token")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    private AvailableTime createSlot(LocalDateTime start, LocalDateTime end) {
        return availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(start)
                .endTime(end)
                .status(AvailableTimeStatus.OPEN)
                .build());
    }
}