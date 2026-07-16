package com.example.booking.payment.adapter.out.pg.dto;

import java.time.LocalDateTime;

public record PgApproveResponse(
        String pgTransactionId,
        LocalDateTime approvedAt
) {

}