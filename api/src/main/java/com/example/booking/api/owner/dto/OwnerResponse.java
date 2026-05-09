package com.example.booking.api.owner.dto;

import com.example.booking.api.owner.domain.Owner;
import com.example.booking.api.owner.domain.OwnerType;

import java.time.LocalDateTime;

public record OwnerResponse(
        Long id,
        Long userId,
        String name,
        String phone,
        OwnerType type,
        LocalDateTime createdAt
) {

    public static OwnerResponse from(Owner owner) {
        return new OwnerResponse(
                owner.getId(),
                owner.getUserId(),
                owner.getName(),
                owner.getPhone(),
                owner.getType(),
                owner.getCreatedAt()
        );
    }
}
