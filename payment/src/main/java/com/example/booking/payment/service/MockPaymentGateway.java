package com.example.booking.payment.service;

import com.example.booking.payment.domain.Payment;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway {

    public void charge(Payment payment) {
        // Mock: 항상 성공. 실제 PG 연동 시 교체 지점
    }

}