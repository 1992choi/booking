package com.example.booking.api.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record SendAdminMessageRequest(@NotBlank String message) {

}