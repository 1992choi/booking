package com.example.booking.api.merchant.dto;

import com.example.booking.api.merchant.domain.Merchant;
import com.example.booking.api.merchant.domain.MerchantType;

public record MerchantSummaryResponse(
        Long id,
        String name,
        MerchantType type
) {
    public static MerchantSummaryResponse from(Merchant merchant) {
        return new MerchantSummaryResponse(
                merchant.getId(),
                merchant.getName(),
                merchant.getType()
        );
    }
}
