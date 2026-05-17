package com.example.booking.reservation.client;

public record ResourceSnapshot(
        Long id,
        String name,
        Long price,
        Integer maxCapacity,
        Long merchantId
) {
}