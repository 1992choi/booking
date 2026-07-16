package com.example.booking.payment.adapter.out.persistence;

import com.example.booking.payment.domain.Payment;

final class PaymentMapper {

    private PaymentMapper() {
    }

    static Payment toDomain(PaymentJpaEntity entity) {
        return Payment.reconstruct(
                entity.getId(),
                entity.getReservationId(),
                entity.getUserId(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getPgTransactionId(),
                entity.getPaidAt(),
                entity.getFailedReason()
        );
    }

    static PaymentJpaEntity toNewJpaEntity(Payment payment) {
        return PaymentJpaEntity.builder()
                .reservationId(payment.getReservationId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .pgTransactionId(payment.getPgTransactionId())
                .paidAt(payment.getPaidAt())
                .failedReason(payment.getFailedReason())
                .build();
    }

    static PaymentJpaEntity updateJpaEntity(PaymentJpaEntity existing, Payment payment) {
        existing.applyState(payment.getStatus(), payment.getPgTransactionId(), payment.getPaidAt(), payment.getFailedReason());

        return existing;
    }

}
