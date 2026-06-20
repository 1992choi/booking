package com.example.booking.reservation.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "daily_merchant_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stat_date", "merchant_id"}))
@Getter
@NoArgsConstructor
public class DailyMerchantStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate statDate;

    @Column(nullable = false)
    private Long merchantId;

    @Column(nullable = false)
    private int confirmedCount;

    @Column(nullable = false)
    private int cancelledCount;

    @Column(nullable = false)
    private Long totalRevenue;

}
