package com.example.stock.domain.market;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@ToString
@Entity(name = "TB_TRADE_HISTORY")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
public class TradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRADE_HISTORY_ID")
    private Long tradeHistoryId;

    @Column(name = "TRADE_DATE")
    private LocalDate tradeDate;

    @Column(name = "MARKET_CODE")
    private String marketCode;

    @Column(name = "TRADE_PRICE")
    private BigDecimal tradePrice;

    @Column(name = "IS_SOLD")
    private Boolean isSold = false;

    @Column(name = "SOLD_PRICE")
    private BigDecimal soldPrice;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public TradeHistory(LocalDate tradeDate, String marketCode, BigDecimal tradePrice) {
        this.tradeDate = tradeDate;
        this.marketCode = marketCode;
        this.tradePrice = tradePrice;
        this.isSold = false;
    }

    public void markAsSold(BigDecimal soldPrice) {
        this.isSold = true;
        this.soldPrice = soldPrice;
    }

}
