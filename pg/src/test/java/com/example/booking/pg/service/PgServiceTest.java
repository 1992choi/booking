package com.example.booking.pg.service;

import com.example.booking.pg.dto.ApproveRequest;
import com.example.booking.pg.dto.ApproveResponse;
import com.example.booking.pg.dto.CancelRequest;
import com.example.booking.pg.dto.CancelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgServiceTest {

    PgService pgService;

    @BeforeEach
    void setUp() {
        pgService = new PgService();
    }

    @Test
    @DisplayName("실패율이 0이면 승인 요청은 항상 성공한다")
    void approve_zeroFailureRate_alwaysSucceeds() {
        ReflectionTestUtils.setField(pgService, "failureRate", 0.0);

        ApproveResponse response = pgService.approve(new ApproveRequest("TXN-1", 10000L));

        assertThat(response.pgTransactionId()).startsWith("PG-");
        assertThat(response.approvedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패율이 1이면 승인 요청은 항상 거절된다")
    void approve_fullFailureRate_alwaysDeclines() {
        ReflectionTestUtils.setField(pgService, "failureRate", 1.0);

        assertThatThrownBy(() -> pgService.approve(new ApproveRequest("TXN-1", 10000L)))
                .isInstanceOf(TransactionDeclinedException.class);
    }

    @Test
    @DisplayName("실패율이 0이면 취소 요청은 항상 성공하고 원 거래 ID를 유지한다")
    void cancel_zeroFailureRate_alwaysSucceeds() {
        ReflectionTestUtils.setField(pgService, "failureRate", 0.0);

        CancelResponse response = pgService.cancel(new CancelRequest("PG-1"));

        assertThat(response.pgTransactionId()).isEqualTo("PG-1");
        assertThat(response.cancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("실패율이 1이면 취소 요청은 항상 거절된다")
    void cancel_fullFailureRate_alwaysDeclines() {
        ReflectionTestUtils.setField(pgService, "failureRate", 1.0);

        assertThatThrownBy(() -> pgService.cancel(new CancelRequest("PG-1")))
                .isInstanceOf(TransactionDeclinedException.class);
    }

}
