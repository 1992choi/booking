package com.example.booking.pg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ApproveRequest(
        @NotBlank String transactionId,
        @Positive long amount
) {

}
