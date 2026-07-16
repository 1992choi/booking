package com.example.booking.payment.application.port.in;

public record ChargePaymentCommand(
        Long reservationId,
        Long userId,
        Long amount
) {

}
