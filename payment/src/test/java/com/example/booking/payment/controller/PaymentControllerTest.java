package com.example.booking.payment.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.payment.domain.Payment;
import com.example.booking.payment.domain.PaymentRepository;
import com.example.booking.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class PaymentControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    PaymentRepository paymentRepository;

    @MockitoBean
    JwtVerifier jwtVerifier;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(1L, Role.USER));
    }

    @Test
    void getByReservationId_success() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
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
    void getByReservationId_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{reservationId}", 9999L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAY_003"));
    }

    @Test
    void refund_success() throws Exception {
        Payment payment = Payment.builder()
                .reservationId(2L)
                .userId(1L)
                .amount(100000L)
                .status(PaymentStatus.COMPLETED)
                .build();
        payment.complete();
        paymentRepository.save(payment);

        mockMvc.perform(post("/api/v1/payments/{reservationId}/refund", 2L)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refund_notAllowed() throws Exception {
        paymentRepository.save(Payment.builder()
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
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{reservationId}", 1L))
                .andExpect(status().isUnauthorized());
    }
}