package com.example.booking.payment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {

    private final Long id;
    private final Long reservationId;
    private final Long userId;
    private final Long amount;
    private PaymentStatus status;
    private String pgTransactionId;
    private LocalDateTime paidAt;
    private String failedReason;

    public static Payment createPending(Long reservationId, Long userId, Long amount) {
        return new Payment(null, reservationId, userId, amount, PaymentStatus.PENDING, null, null, null);
    }

    public static Payment reconstruct(Long id, Long reservationId, Long userId, Long amount, PaymentStatus status,
                                       String pgTransactionId, LocalDateTime paidAt, String failedReason) {
        return new Payment(id, reservationId, userId, amount, status, pgTransactionId, paidAt, failedReason);
    }

    public void complete(String pgTransactionId) {
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failedReason = reason;
    }

    public void ensureRefundable() {
        if (status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("환불 가능 상태가 아닙니다: " + status);
        }
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }

}
