package com.example.stock.controller.market;

import com.example.stock.domain.market.TradeHistory;
import com.example.stock.dto.TradeHistoryDto;
import com.example.stock.service.market.MarketService;
import com.example.stock.service.trade.TradeHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    private final TradeHistoryService tradeHistoryService;

    @PostMapping("/api/balance/notifications")
    public void sendBalanceToTelegram() {
        marketService.sendBalanceToTelegram();
    }

    @PostMapping("/api/trade-history")
    public TradeHistory saveTradeHistory(@RequestBody TradeHistoryDto tradeHistoryDto) {
        return tradeHistoryService.save(tradeHistoryDto);
    }

    @DeleteMapping("/api/trade-history")
    public void deleteTradeHistory() {
        tradeHistoryService.delete();
    }

    @GetMapping("/api/trade-histories")
    public List<TradeHistoryDto> getTradeHistories() {
        return tradeHistoryService.getTradeHistories();
    }

}
