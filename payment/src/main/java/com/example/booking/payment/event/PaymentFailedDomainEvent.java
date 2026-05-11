package com.example.booking.payment.event;

import com.example.booking.payment.domain.Payment;

public record PaymentFailedDomainEvent(Payment payment) {}