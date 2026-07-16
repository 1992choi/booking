package com.example.booking.payment.application.port.out;

import com.example.booking.payment.domain.Payment;

public interface SavePaymentPort {

    Payment save(Payment payment);

}
