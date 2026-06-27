package com.example.booking.payment.pg.dto;

public record PgCancelRequest(
        String pgTransactionId
) {

}