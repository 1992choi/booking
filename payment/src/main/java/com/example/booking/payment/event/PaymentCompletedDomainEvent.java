package com.example.booking.payment.event;

import com.example.booking.payment.domain.Payment;

public record PaymentCompletedDomainEvent(Payment payment) {}