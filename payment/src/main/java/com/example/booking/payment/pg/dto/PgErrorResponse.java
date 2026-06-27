package com.example.booking.payment.pg.dto;

public record PgErrorResponse(
        String code,
        String message
) {

}