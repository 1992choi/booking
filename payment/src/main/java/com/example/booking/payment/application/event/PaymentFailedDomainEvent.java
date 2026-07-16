package com.example.booking.payment.application.event;

import com.example.booking.payment.domain.Payment;

public record PaymentFailedDomainEvent(Payment payment) {}
