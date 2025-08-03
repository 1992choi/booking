package com.example.stock.repository.market;

import com.example.stock.domain.market.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    List<MarketPrice> findTop20ByMarketCodeOrderByCreatedAtDesc(String marketCode);

}
