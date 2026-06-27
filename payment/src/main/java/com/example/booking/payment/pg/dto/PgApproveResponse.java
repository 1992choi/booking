package com.example.booking.payment.pg.dto;

import java.time.LocalDateTime;

public record PgApproveResponse(
        String pgTransactionId,
        LocalDateTime approvedAt
) {

}