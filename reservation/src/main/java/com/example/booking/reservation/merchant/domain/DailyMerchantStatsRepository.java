package com.example.booking.reservation.merchant.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyMerchantStatsRepository extends JpaRepository<DailyMerchantStats, Long> {

    List<DailyMerchantStats> findAllByMerchantIdAndStatDateBetweenOrderByStatDateAsc(Long merchantId, LocalDate from, LocalDate to);

}
