package com.example.booking.pg.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(
        @NotBlank String pgTransactionId
) {

}
