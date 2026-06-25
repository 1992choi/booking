package com.example.booking.pg.dto;

public record PgErrorResponse(
        String code,
        String message
) {

}
