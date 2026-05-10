package com.example.booking.api.owner.dto;

import com.example.booking.api.owner.domain.Owner;
import com.example.booking.api.owner.domain.OwnerType;

public record OwnerSummaryResponse(
        Long id,
        String name,
        OwnerType type
) {

    public static OwnerSummaryResponse from(Owner owner) {
        return new OwnerSummaryResponse(
                owner.getId(),
                owner.getName(),
                owner.getType()
        );
    }
}