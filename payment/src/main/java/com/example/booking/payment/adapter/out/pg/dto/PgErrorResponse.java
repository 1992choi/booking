package com.example.booking.payment.adapter.out.pg.dto;

public record PgErrorResponse(
        String code,
        String message
) {

}