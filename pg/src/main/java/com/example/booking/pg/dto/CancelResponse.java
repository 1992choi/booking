package com.example.booking.pg.dto;

import java.time.LocalDateTime;

public record CancelResponse(
        String pgTransactionId,
        LocalDateTime cancelledAt
) {

}
