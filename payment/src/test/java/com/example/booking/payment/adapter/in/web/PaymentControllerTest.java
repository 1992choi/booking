package com.example.booking.payment.adapter.in.web;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.payment.adapter.out.persistence.PaymentJpaEntity;
import com.example.booking.payment.adapter.out.persistence.PaymentJpaRepository;
import com.example.booking.payment.application.port.out.PgClientPort;
import com.example.booking.payment.domain.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PaymentControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    PaymentJpaRepository paymentJpaRepository;

    @MockitoBean
    JwtVerifier jwtVerifier;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    PgClientPort pgClientPort;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));
        paymentJpaRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        paymentJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("예약 ID로 결제 정보 조회 성공")
    void getByReservationId_success() throws Exception {
        PaymentJpaEntity payment = paymentJpaRepository.save(PaymentJpaEntity.builder()
                .reservationId(1L)
                .userId(1L)
                .amount(150000L)
                .status(PaymentStatus.COMPLETED)
                .build());

        mockMvc.perform(get("/api/v1/payments/{reservationId}", 1L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId()))
                .andExpect(jsonPath("$.reservationId").value(1))
                .andExpect(jsonPath("$.amount").value(150000))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("존재하지 않는 예약의 결제 조회 시 404 반환")
    void getByReservationId_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{reservationId}", 9999L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAY_003"));
    }

    @Test
    @DisplayName("COMPLETED 상태 결제 환불 성공")
    void refund_success() throws Exception {
        PaymentJpaEntity payment = PaymentJpaEntity.builder()
                .reservationId(2L)
                .userId(1L)
                .amount(100000L)
                .status(PaymentStatus.COMPLETED)
                .pgTransactionId("PG-TEST-001")
                .build();
        paymentJpaRepository.save(payment);

        mockMvc.perform(post("/api/v1/payments/{reservationId}/refund", 2L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("PENDING 상태 결제 환불 시도 시 409 반환")
    void refund_notAllowed() throws Exception {
        paymentJpaRepository.save(PaymentJpaEntity.builder()
                .reservationId(3L)
                .userId(1L)
                .amount(100000L)
                .status(PaymentStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/v1/payments/{reservationId}/refund", 3L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAY_002"));
    }

    @Test
    @DisplayName("비인증 요청으로 결제 조회 시 401 반환")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{reservationId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미 환불된 결제 재환불 시도 시 409 반환")
    void refund_alreadyRefunded() throws Exception {
        PaymentJpaEntity payment = PaymentJpaEntity.builder()
                .reservationId(4L)
                .userId(1L)
                .amount(100000L)
                .status(PaymentStatus.COMPLETED)
                .pgTransactionId("PG-TEST-001")
                .build();
        paymentJpaRepository.save(payment);

        mockMvc.perform(post("/api/v1/payments/{reservationId}/refund", 4L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(post("/api/v1/payments/{reservationId}/refund", 4L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAY_002"));
    }

    @Test
    @DisplayName("존재하지 않는 예약 환불 시 404 반환")
    void refund_notFound() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{reservationId}/refund", 9999L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAY_003"));
    }

    @Test
    @DisplayName("결제 조회 시 모든 필드 정상 반환")
    void getByReservationId_verifyAllFields() throws Exception {
        PaymentJpaEntity payment = paymentJpaRepository.save(PaymentJpaEntity.builder()
                .reservationId(5L)
                .userId(1L)
                .amount(200000L)
                .status(PaymentStatus.COMPLETED)
                .build());

        mockMvc.perform(get("/api/v1/payments/{reservationId}", 5L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId()))
                .andExpect(jsonPath("$.reservationId").value(5))
                .andExpect(jsonPath("$.amount").value(200000))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paidAt").isEmpty());
    }
}
