package com.example.booking.payment.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.payment.domain.Payment;
import com.example.booking.payment.domain.PaymentRepository;
import com.example.booking.payment.domain.PaymentStatus;
import com.example.booking.payment.dto.PaymentResponse;
import com.example.booking.payment.error.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse process(Long reservationId, Long userId, Long amount) {
        Payment payment = Payment.builder()
                .reservationId(reservationId)
                .userId(userId)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        // Mock: 항상 성공
        payment.complete();
        return PaymentResponse.from(payment);
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

        payment.refund();
        return PaymentResponse.from(payment);
    }
}