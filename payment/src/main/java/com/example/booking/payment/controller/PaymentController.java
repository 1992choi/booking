package com.example.booking.payment.controller;

import com.example.booking.payment.dto.PaymentResponse;
import com.example.booking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/api/v1/payments/{reservationId}")
    public PaymentResponse getByReservationId(@PathVariable Long reservationId) {
        return paymentService.getByReservationId(reservationId);
    }

    @PostMapping("/api/v1/payments/{reservationId}/refund")
    public PaymentResponse refund(@PathVariable Long reservationId) {
        return paymentService.refund(reservationId);
    }
}