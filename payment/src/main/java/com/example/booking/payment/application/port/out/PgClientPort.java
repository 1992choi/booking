package com.example.booking.payment.application.port.out;

import com.example.booking.payment.domain.Payment;

public interface PgClientPort {

    String charge(Payment payment);

    void cancel(String pgTransactionId);

}
