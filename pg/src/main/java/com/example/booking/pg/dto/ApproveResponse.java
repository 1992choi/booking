package com.example.booking.pg.dto;

import java.time.LocalDateTime;

public record ApproveResponse(
        String pgTransactionId,
        LocalDateTime approvedAt
) {

}
