package com.example.booking.payment.service;

import com.example.booking.payment.domain.PaymentRepository;
import com.example.booking.payment.event.ReservationCreatedKafkaEvent;
import com.example.booking.payment.pg.PgGateway;
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
    PaymentService paymentService;

    @Autowired
    PaymentRepository paymentRepository;

    @MockitoBean
    PgGateway pgGateway;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        given(pgGateway.charge(any())).willReturn("PG-TEST-001");
    }

    @Test
    @DisplayName("같은 예약의 결제 생성 이벤트가 중복 수신되면 두 번째는 저장하지 않는다")
    void process_duplicateReservationId_skipsSecondInsert() {
        ReservationCreatedKafkaEvent event = new ReservationCreatedKafkaEvent(10L, 1L, 100L, 50000L);

        paymentService.process(event);
        paymentService.process(event);

        assertThat(paymentRepository.findByReservationId(10L)).isPresent();
        verify(pgGateway, times(1)).charge(any());
    }

}