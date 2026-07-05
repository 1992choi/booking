package com.example.booking.payment.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.payment.domain.Payment;
import com.example.booking.payment.domain.PaymentRepository;
import com.example.booking.payment.domain.PaymentStatus;
import com.example.booking.payment.dto.PaymentResponse;
import com.example.booking.payment.error.PaymentErrorCode;
import com.example.booking.payment.event.PaymentCompletedDomainEvent;
import com.example.booking.payment.event.PaymentFailedDomainEvent;
import com.example.booking.payment.event.ReservationCreatedKafkaEvent;
import com.example.booking.payment.pg.PgGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PgGateway pgGateway;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void process(ReservationCreatedKafkaEvent event) {
        if (paymentRepository.findByReservationId(event.reservationId()).isPresent()) {
            log.info("중복 결제 요청 스킵 reservationId={}", event.reservationId());
            return;
        }

        log.info("결제 처리 시작 reservationId={}, userId={}, amount={}", event.reservationId(), event.userId(), event.amount());

        Payment payment = Payment.builder()
                .reservationId(event.reservationId())
                .userId(event.userId())
                .amount(event.amount())
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        try {
            String pgTransactionId = pgGateway.charge(payment);
            payment.complete(pgTransactionId);
            eventPublisher.publishEvent(new PaymentCompletedDomainEvent(payment));
            log.info("결제 완료 paymentId={}, reservationId={}", payment.getId(), event.reservationId());
        } catch (PaymentDeclinedException e) {
            payment.fail(e.getMessage());
            eventPublisher.publishEvent(new PaymentFailedDomainEvent(payment));
            log.warn("결제 실패 reservationId={}, reason={}", event.reservationId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByReservationId(Long reservationId) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse refund(Long reservationId) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new BusinessException(PaymentErrorCode.REFUND_NOT_ALLOWED);
        }

        try {
            pgGateway.cancel(payment.getPgTransactionId());
        } catch (PaymentDeclinedException e) {
            log.warn("PG 환불 실패 paymentId={}, reason={}", payment.getId(), e.getMessage());
            throw new BusinessException(PaymentErrorCode.REFUND_FAILED);
        }

        payment.refund();
        log.info("환불 처리 paymentId={}, reservationId={}", payment.getId(), reservationId);

        return PaymentResponse.from(payment);
    }

}