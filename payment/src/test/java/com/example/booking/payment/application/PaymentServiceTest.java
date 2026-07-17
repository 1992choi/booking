package com.example.booking.payment.application;

import com.example.booking.payment.adapter.out.persistence.PaymentJpaEntity;
import com.example.booking.payment.adapter.out.persistence.PaymentJpaRepository;
import com.example.booking.payment.application.port.in.ChargePaymentCommand;
import com.example.booking.payment.application.port.in.ChargePaymentUseCase;
import com.example.booking.payment.application.port.in.RefundPaymentUseCase;
import com.example.booking.payment.application.port.out.PgClientPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class PaymentServiceTest {

    @Autowired
    ChargePaymentUseCase chargePaymentUseCase;

    @Autowired
    RefundPaymentUseCase refundPaymentUseCase;

    @Autowired
    PaymentJpaRepository paymentJpaRepository;

    @MockitoBean
    PgClientPort pgClientPort;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        paymentJpaRepository.deleteAll();
        given(pgClientPort.charge(any())).willReturn("PG-TEST-001");
    }

    @Test
    @DisplayName("같은 예약의 결제 생성 이벤트가 중복 수신되면 두 번째는 저장하지 않는다")
    void charge_duplicateReservationId_skipsSecondInsert() {
        ChargePaymentCommand command = new ChargePaymentCommand(10L, 1L, 50000L);

        chargePaymentUseCase.charge(command);
        chargePaymentUseCase.charge(command);

        assertThat(paymentJpaRepository.findByReservationId(10L)).isPresent();
        verify(pgClientPort, times(1)).charge(any());
    }

    @Test
    @DisplayName("생성 후 상태가 여러 번 갱신돼도 createdAt은 최초 생성 시각을 유지한다 (영속성 어댑터의 merge 함정 회귀 테스트)")
    void save_multipleUpdates_preservesCreatedAt() {
        chargePaymentUseCase.charge(new ChargePaymentCommand(20L, 1L, 50000L));

        PaymentJpaEntity afterCharge = paymentJpaRepository.findByReservationId(20L).orElseThrow();
        LocalDateTime createdAtAfterCharge = afterCharge.getCreatedAt();
        assertThat(createdAtAfterCharge).isNotNull();

        refundPaymentUseCase.refund(20L);

        PaymentJpaEntity afterRefund = paymentJpaRepository.findByReservationId(20L).orElseThrow();
        assertThat(afterRefund.getCreatedAt()).isEqualTo(createdAtAfterCharge);
        assertThat(afterRefund.getStatus().name()).isEqualTo("REFUNDED");
    }

}
