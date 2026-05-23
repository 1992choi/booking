package com.example.booking.reservation.merchant.dto;

import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantType;

import java.time.LocalDateTime;

public record MerchantResponse(
        Long id,
        Long userId,
        String name,
        String phone,
        MerchantType type,
        LocalDateTime createdAt
) {
    public static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getUserId(),
                merchant.getName(),
                merchant.getPhone(),
                merchant.getType(),
                merchant.getCreatedAt()
        );
    }
}
