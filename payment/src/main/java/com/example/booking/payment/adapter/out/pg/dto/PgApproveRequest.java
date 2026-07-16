package com.example.booking.payment.adapter.out.pg.dto;

public record PgApproveRequest(
        String transactionId,
        long amount
) {

}