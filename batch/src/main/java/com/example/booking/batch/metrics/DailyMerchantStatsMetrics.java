package com.example.booking.batch.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class DailyMerchantStatsMetrics {

    private final MeterRegistry meterRegistry;

    private final Map<Long, AtomicLong> confirmedCounts = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> cancelledCounts = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> revenues = new ConcurrentHashMap<>();

    public void record(Long merchantId, long confirmedCount, long cancelledCount, long totalRevenue) {
        gaugeFor(confirmedCounts, merchantId, "merchant.daily.reservation.count", "CONFIRMED").set(confirmedCount);
        gaugeFor(cancelledCounts, merchantId, "merchant.daily.reservation.count", "CANCELLED").set(cancelledCount);
        gaugeFor(revenues, merchantId, "merchant.daily.revenue", null).set(totalRevenue);
    }

    private AtomicLong gaugeFor(Map<Long, AtomicLong> store, Long merchantId, String name, String status) {
        return store.computeIfAbsent(merchantId, id -> {
            AtomicLong value = new AtomicLong();
            Tags tags = status == null
                    ? Tags.of("merchantId", id.toString())
                    : Tags.of("merchantId", id.toString(), "status", status);
            meterRegistry.gauge(name, tags, value);

            return value;
        });
    }

}
