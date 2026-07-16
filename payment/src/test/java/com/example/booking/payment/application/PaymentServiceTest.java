package com.example.booking.payment.application;

import com.example.booking.payment.adapter.out.persistence.PaymentJpaRepository;
import com.example.booking.payment.application.port.in.ChargePaymentCommand;
import com.example.booking.payment.application.port.in.ChargePaymentUseCase;
import com.example.booking.payment.application.port.out.PgClientPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

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

}
