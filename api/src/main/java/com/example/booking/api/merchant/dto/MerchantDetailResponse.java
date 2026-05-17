package com.example.booking.api.merchant.dto;

import com.example.booking.api.merchant.domain.Merchant;
import com.example.booking.api.merchant.domain.MerchantType;
import com.example.booking.api.resource.domain.Resource;
import com.example.booking.api.resource.dto.ResourceResponse;

import java.util.List;

public record MerchantDetailResponse(
        Long id,
        String name,
        MerchantType type,
        List<ResourceResponse> resources
) {
    public static MerchantDetailResponse from(Merchant merchant, List<Resource> resources) {
        return new MerchantDetailResponse(
                merchant.getId(),
                merchant.getName(),
                merchant.getType(),
                resources.stream().map(ResourceResponse::from).toList()
        );
    }
}
