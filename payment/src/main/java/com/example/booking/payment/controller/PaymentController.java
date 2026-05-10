package com.example.booking.payment.controller;

import com.example.booking.payment.dto.PaymentResponse;
import com.example.booking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{reservationId}")
    public PaymentResponse getByReservationId(@PathVariable Long reservationId) {
        return paymentService.getByReservationId(reservationId);
    }

    @PostMapping("/{reservationId}/refund")
    public PaymentResponse refund(@PathVariable Long reservationId) {
        return paymentService.refund(reservationId);
    }
}