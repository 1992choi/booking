package com.example.booking.payment.pg.dto;

public record PgApproveRequest(
        String transactionId,
        long amount
) {

}