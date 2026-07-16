package com.example.booking.payment.application.port.in;

public interface GetPaymentUseCase {

    PaymentResponse getByReservationId(Long reservationId);

}
