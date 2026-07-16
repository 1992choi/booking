package com.example.booking.payment.application;

import com.example.booking.core.error.BusinessException;
import com.example.booking.payment.application.event.PaymentCompletedDomainEvent;
import com.example.booking.payment.application.event.PaymentFailedDomainEvent;
import com.example.booking.payment.application.port.in.ChargePaymentCommand;
import com.example.booking.payment.application.port.in.ChargePaymentUseCase;
import com.example.booking.payment.application.port.in.GetPaymentUseCase;
import com.example.booking.payment.application.port.in.PaymentResponse;
import com.example.booking.payment.application.port.in.RefundPaymentUseCase;
import com.example.booking.payment.application.port.out.LoadPaymentPort;
import com.example.booking.payment.application.port.out.PaymentDeclinedException;
import com.example.booking.payment.application.port.out.PgClientPort;
import com.example.booking.payment.application.port.out.SavePaymentPort;
import com.example.booking.payment.domain.Payment;
import com.example.booking.payment.error.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements ChargePaymentUseCase, RefundPaymentUseCase, GetPaymentUseCase {

    private final LoadPaymentPort loadPaymentPort;
    private final SavePaymentPort savePaymentPort;
    private final PgClientPort pgClientPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void charge(ChargePaymentCommand command) {
        if (loadPaymentPort.loadByReservationId(command.reservationId()).isPresent()) {
            log.info("중복 결제 요청 스킵 reservationId={}", command.reservationId());
            return;
        }

        log.info("결제 처리 시작 reservationId={}, userId={}, amount={}", command.reservationId(), command.userId(), command.amount());

        Payment payment = Payment.createPending(command.reservationId(), command.userId(), command.amount());
        payment = savePaymentPort.save(payment);

        try {
            String pgTransactionId = pgClientPort.charge(payment);
            payment.complete(pgTransactionId);
            savePaymentPort.save(payment);
            eventPublisher.publishEvent(new PaymentCompletedDomainEvent(payment));
            log.info("결제 완료 paymentId={}, reservationId={}", payment.getId(), command.reservationId());
        } catch (PaymentDeclinedException e) {
            payment.fail(e.getMessage());
            savePaymentPort.save(payment);
            eventPublisher.publishEvent(new PaymentFailedDomainEvent(payment));
            log.warn("결제 실패 reservationId={}, reason={}", command.reservationId(), e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByReservationId(Long reservationId) {
        Payment payment = loadPaymentPort.loadByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));

        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refund(Long reservationId) {
        Payment payment = loadPaymentPort.loadByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.NOT_FOUND));

        try {
            payment.ensureRefundable();
        } catch (IllegalStateException e) {
            throw new BusinessException(PaymentErrorCode.REFUND_NOT_ALLOWED);
        }

        try {
            pgClientPort.cancel(payment.getPgTransactionId());
        } catch (PaymentDeclinedException e) {
            log.warn("PG 환불 실패 paymentId={}, reason={}", payment.getId(), e.getMessage());
            throw new BusinessException(PaymentErrorCode.REFUND_FAILED);
        }

        payment.refund();
        savePaymentPort.save(payment);
        log.info("환불 처리 paymentId={}, reservationId={}", payment.getId(), reservationId);

        return PaymentResponse.from(payment);
    }

}
