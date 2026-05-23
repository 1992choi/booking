package com.example.booking.reservation.merchant.dto;

import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantType;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.dto.ResourceResponse;

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
