package com.example.booking.payment.adapter.in.web;

import com.example.booking.payment.application.port.in.GetPaymentUseCase;
import com.example.booking.payment.application.port.in.PaymentResponse;
import com.example.booking.payment.application.port.in.RefundPaymentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final GetPaymentUseCase getPaymentUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;

    @GetMapping("/api/v1/payments/{reservationId}")
    public PaymentResponse getByReservationId(@PathVariable Long reservationId) {
        return getPaymentUseCase.getByReservationId(reservationId);
    }

    @PostMapping("/api/v1/payments/{reservationId}/refund")
    public PaymentResponse refund(@PathVariable Long reservationId) {
        return refundPaymentUseCase.refund(reservationId);
    }

}
