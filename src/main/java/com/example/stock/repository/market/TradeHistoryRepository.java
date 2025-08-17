package com.example.stock.repository.market;

import com.example.stock.domain.market.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {

    List<TradeHistory> findByMarketCode(String marketCode);

    List<TradeHistory> findByIsSold(Boolean isSold);

    Optional<TradeHistory> findTopByMarketCodeAndIsSoldFalseOrderByCreatedAtAsc(String marketCode);

    boolean existsByIsSoldFalseAndMarketCode(String marketCode);

}
