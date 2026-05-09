package com.example.booking.api.resource.dto;

import com.example.booking.api.resource.domain.Resource;

public record ResourceResponse(
        Long id,
        Long ownerId,
        String name,
        String description,
        Long price,
        Integer maxCapacity
) {

    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getOwnerId(),
                resource.getName(),
                resource.getDescription(),
                resource.getPrice(),
                resource.getMaxCapacity()
        );
    }
}