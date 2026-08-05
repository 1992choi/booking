package com.example.booking.pg.controller;

import com.example.booking.pg.dto.ApproveRequest;
import com.example.booking.pg.dto.ApproveResponse;
import com.example.booking.pg.dto.CancelRequest;
import com.example.booking.pg.dto.CancelResponse;
import com.example.booking.pg.service.PgService;
import com.example.booking.pg.service.TransactionDeclinedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PgControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PgService pgService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    @DisplayName("결제 승인 성공 시 200과 PG 거래 ID 반환")
    void approve_success() throws Exception {
        given(pgService.approve(new ApproveRequest("TXN-1", 10000L)))
                .willReturn(new ApproveResponse("PG-123", LocalDateTime.now()));

        mockMvc.perform(post("/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRequest("TXN-1", 10000L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pgTransactionId").value("PG-123"));
    }

    @Test
    @DisplayName("결제 승인 거절 시 402 반환")
    void approve_declined() throws Exception {
        willThrow(new TransactionDeclinedException("잔액 부족"))
                .given(pgService).approve(new ApproveRequest("TXN-1", 10000L));

        mockMvc.perform(post("/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRequest("TXN-1", 10000L))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("TRANSACTION_DECLINED"))
                .andExpect(jsonPath("$.message").value("잔액 부족"));
    }

    @Test
    @DisplayName("금액이 0 이하이면 400 반환")
    void approve_invalidAmount() throws Exception {
        mockMvc.perform(post("/pg/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveRequest("TXN-1", 0L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("결제 취소 성공 시 200과 원 거래 ID 반환")
    void cancel_success() throws Exception {
        given(pgService.cancel(new CancelRequest("PG-123")))
                .willReturn(new CancelResponse("PG-123", LocalDateTime.now()));

        mockMvc.perform(post("/pg/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelRequest("PG-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pgTransactionId").value("PG-123"));
    }

    @Test
    @DisplayName("결제 취소 거절 시 402 반환")
    void cancel_declined() throws Exception {
        willThrow(new TransactionDeclinedException("이미 취소된 거래"))
                .given(pgService).cancel(new CancelRequest("PG-123"));

        mockMvc.perform(post("/pg/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelRequest("PG-123"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("TRANSACTION_DECLINED"));
    }

}
