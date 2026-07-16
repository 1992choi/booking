package com.example.booking.payment.application.port.out;

import com.example.booking.payment.domain.Payment;

import java.util.Optional;

public interface LoadPaymentPort {

    Optional<Payment> loadByReservationId(Long reservationId);

}
