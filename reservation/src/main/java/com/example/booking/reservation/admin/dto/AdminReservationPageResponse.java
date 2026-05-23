package com.example.booking.reservation.admin.dto;

import java.util.List;

public record AdminReservationPageResponse(
        List<AdminReservationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
