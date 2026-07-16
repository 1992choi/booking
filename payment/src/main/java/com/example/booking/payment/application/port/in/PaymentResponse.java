package com.example.booking.payment.application.port.in;

import com.example.booking.payment.domain.Payment;
import com.example.booking.payment.domain.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long reservationId,
        Long amount,
        PaymentStatus status,
        LocalDateTime paidAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getReservationId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt()
        );
    }
}
