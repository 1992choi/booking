package com.example.booking.reservation.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.dto.CreateReservationRequest;
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

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    AvailableTimeRepository availableTimeRepository;

    @MockitoBean
    JwtVerifier jwtVerifier;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    MockMvc mockMvc;

    Resource resource;
    AvailableTime slot;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));

        resource = resourceRepository.save(Resource.builder()
                .merchantId(10L)
                .name("별채 A")
                .description("2인실")
                .price(150000L)
                .maxCapacity(2)
                .build());

        slot = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(LocalDateTime.of(2026, 6, 1, 14, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 15, 0))
                .status(AvailableTimeStatus.OPEN)
                .build());
    }

    @Test
    @DisplayName("예약 생성 성공 시 PENDING 상태 및 리소스 정보 반환")
    void create_success() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 2);

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
    @DisplayName("최대 인원 초과 예약 시 422 반환")
    void create_capacityExceeded() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 3);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RSV_003"));
    }

    @Test
    @DisplayName("BLOCKED 상태 슬롯으로 예약 시 409 반환")
    void create_slotNotOpen() throws Exception {
        AvailableTime blockedSlot = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(LocalDateTime.of(2026, 6, 2, 14, 0))
                .endTime(LocalDateTime.of(2026, 6, 2, 15, 0))
                .status(AvailableTimeStatus.BLOCKED)
                .build());

        CreateReservationRequest request = new CreateReservationRequest(resource.getId(), List.of(blockedSlot.getId()), 1);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV_001"));
    }

    @Test
    @DisplayName("다른 리소스 소속 슬롯으로 예약 시 409 반환")
    void create_slotBelongsToDifferentResource() throws Exception {
        Resource otherResource = resourceRepository.save(Resource.builder()
                .merchantId(10L).name("별채 B").description("").price(100000L).maxCapacity(4).build());
        AvailableTime otherSlot = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(otherResource.getId())
                .startTime(LocalDateTime.of(2026, 6, 3, 14, 0))
                .endTime(LocalDateTime.of(2026, 6, 3, 15, 0))
                .status(AvailableTimeStatus.OPEN)
                .build());

        CreateReservationRequest request = new CreateReservationRequest(resource.getId(), List.of(otherSlot.getId()), 1);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV_001"));
    }

    @Test
    @DisplayName("최대 인원이 꽉 찬 시간대에 추가 예약 시 409 반환")
    void create_timeConflict() throws Exception {
        // maxCapacity=2 를 꽉 채운 뒤 추가 예약 시도 → CONFLICT
        CreateReservationRequest first = new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 2);
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        AvailableTime overlappingSlot = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(LocalDateTime.of(2026, 6, 1, 14, 30))
                .endTime(LocalDateTime.of(2026, 6, 1, 15, 30))
                .status(AvailableTimeStatus.OPEN)
                .build());

        CreateReservationRequest second = new CreateReservationRequest(resource.getId(), List.of(overlappingSlot.getId()), 1);
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV_001"));
    }

    @Test
    @DisplayName("예약 ID로 단건 조회 성공")
    void getById_success() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1))))
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
    @DisplayName("존재하지 않는 예약 조회 시 404 반환")
    void getById_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/{id}", 9999L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RSV_004"));
    }

    @Test
    @DisplayName("내 예약 목록 조회 성공 — 상태 필터 적용")
    void getMyReservations_success() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer test-token")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.resourceName == '별채 A')]").exists());
    }

    @Test
    @DisplayName("예약 취소 성공 시 CANCELLED 상태 반환")
    void cancel_success() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get(0).get("id").asLong();

        mockMvc.perform(put("/api/v1/reservations/{id}/cancel", reservationId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("본인이 아닌 예약 취소 시 403 반환")
    void cancel_notMyReservation() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1))))
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
    @DisplayName("비인증 요청으로 예약 생성 시 401 반환")
    void create_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("슬롯 목록이 비어있으면 예약 생성 시 400 반환")
    void create_invalidRequest_emptySlots() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":" + resource.getId() + ",\"availableTimeIds\":[],\"headCount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("headCount가 0이면 예약 생성 시 400 반환")
    void create_invalidRequest_headCountZero() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":" + resource.getId() + ",\"availableTimeIds\":[" + slot.getId() + "],\"headCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("상태 파라미터 없이 내 예약 조회 시 기본값 PENDING 상태 반환")
    void getMyReservations_defaultStatusIsPending() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("CONFIRMED 상태 필터로 조회 시 PENDING 예약은 포함되지 않음")
    void getMyReservations_confirmedStatus_returnsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer test-token")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 예약 취소 시 404 반환")
    void cancel_notFound() throws Exception {
        mockMvc.perform(put("/api/v1/reservations/{id}/cancel", 9999L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RSV_004"));
    }

    @Test
    @DisplayName("존재하지 않는 리소스로 예약 생성 시 404 반환")
    void create_resourceNotFound() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(9999L, List.of(slot.getId()), 1);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RSV_007"));
    }

    @Test
    @DisplayName("비인증 요청으로 예약 단건 조회 시 401 반환")
    void getById_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/{id}", 1L))
                .andExpect(status().isUnauthorized());
    }
}