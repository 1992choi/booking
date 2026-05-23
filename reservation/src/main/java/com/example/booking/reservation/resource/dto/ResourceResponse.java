package com.example.booking.reservation.resource.dto;

import com.example.booking.reservation.resource.domain.Resource;

public record ResourceResponse(
        Long id,
        Long merchantId,
        String name,
        String description,
        Long price,
        Integer maxCapacity
) {

    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getMerchantId(),
                resource.getName(),
                resource.getDescription(),
                resource.getPrice(),
                resource.getMaxCapacity()
        );
    }
}
