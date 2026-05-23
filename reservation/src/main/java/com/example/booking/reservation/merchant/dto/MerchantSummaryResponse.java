package com.example.booking.reservation.merchant.dto;

import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantType;

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
