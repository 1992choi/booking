package com.example.booking.payment.application.port.in;

public interface RefundPaymentUseCase {

    PaymentResponse refund(Long reservationId);

}
