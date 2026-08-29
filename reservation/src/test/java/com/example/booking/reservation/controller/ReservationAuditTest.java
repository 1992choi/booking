package com.example.booking.reservation.controller;

import com.example.booking.core.audit.AuditLog;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ReservationAuditTest {

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

    @Autowired
    MongoTemplate mongoTemplate;

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
                .name("감사로그 테스트용 리소스")
                .description("설명")
                .price(10000L)
                .maxCapacity(2)
                .build());

        slot = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(LocalDateTime.of(2026, 7, 1, 10, 0))
                .endTime(LocalDateTime.of(2026, 7, 1, 11, 0))
                .status(AvailableTimeStatus.OPEN)
                .build());
    }

    @Test
    @DisplayName("예약 생성 시 RESERVATION_CREATED 감사 로그가 MongoDB에 기록된다")
    void create_recordsAuditLog() throws Exception {
        CreateReservationRequest request = new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1);

        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = objectMapper.readTree(response).get(0).get("id").asLong();

        List<AuditLog> logs = mongoTemplate.find(
                Query.query(Criteria.where("action").is("RESERVATION_CREATED")
                        .and("detail.reservationIds").is(List.of(reservationId))),
                AuditLog.class, "audit_logs");

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("예약 취소 시 RESERVATION_CANCELLED 감사 로그가 MongoDB에 기록된다")
    void cancel_recordsAuditLog() throws Exception {
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
                .andExpect(status().isOk());

        List<AuditLog> logs = mongoTemplate.find(
                Query.query(Criteria.where("action").is("RESERVATION_CANCELLED")
                        .and("detail.reservationId").is(reservationId)),
                AuditLog.class, "audit_logs");

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).userId()).isEqualTo(1L);
    }

}
