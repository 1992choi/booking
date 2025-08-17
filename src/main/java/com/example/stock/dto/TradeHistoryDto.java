package com.example.stock.dto;

import com.example.stock.domain.market.TradeHistory;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class TradeHistoryDto {

    private String marketCode;
    private BigDecimal tradePrice;

    public TradeHistory toEntity() {
        return TradeHistory.builder()
                .marketCode(this.marketCode)
                .tradePrice(this.tradePrice)
                .tradeDate(LocalDate.now())
                .build();
    }

    public static TradeHistoryDto fromEntity(TradeHistory tradeHistory) {
        TradeHistoryDto dto = new TradeHistoryDto();
        dto.marketCode = tradeHistory.getMarketCode();
        dto.tradePrice = tradeHistory.getTradePrice();

        return dto;
    }

}
