package com.example.booking.reservation.merchant.dto;

import com.example.booking.reservation.merchant.domain.DailyMerchantStats;

import java.time.LocalDate;

public record DailyMerchantStatsResponse(
        LocalDate statDate,
        int confirmedCount,
        int cancelledCount,
        long totalRevenue
) {

    public static DailyMerchantStatsResponse from(DailyMerchantStats stats) {
        return new DailyMerchantStatsResponse(
                stats.getStatDate(),
                stats.getConfirmedCount(),
                stats.getCancelledCount(),
                stats.getTotalRevenue()
        );
    }

}
