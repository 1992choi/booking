package com.example.booking.api.internal.dto;

import com.example.booking.api.resource.domain.Resource;

public record ResourceSnapshot(
        Long id,
        String name,
        Long price,
        Integer maxCapacity,
        Long ownerId
) {

    public static ResourceSnapshot from(Resource resource) {
        return new ResourceSnapshot(
                resource.getId(),
                resource.getName(),
                resource.getPrice(),
                resource.getMaxCapacity(),
                resource.getOwnerId()
        );
    }
}