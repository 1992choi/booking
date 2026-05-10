package com.example.booking.api.owner.dto;

import com.example.booking.api.owner.domain.Owner;
import com.example.booking.api.owner.domain.OwnerType;
import com.example.booking.api.resource.domain.Resource;
import com.example.booking.api.resource.dto.ResourceResponse;

import java.util.List;

public record OwnerDetailResponse(
        Long id,
        String name,
        OwnerType type,
        List<ResourceResponse> resources
) {

    public static OwnerDetailResponse from(Owner owner, List<Resource> resources) {
        return new OwnerDetailResponse(
                owner.getId(),
                owner.getName(),
                owner.getType(),
                resources.stream().map(ResourceResponse::from).toList()
        );
    }
}
