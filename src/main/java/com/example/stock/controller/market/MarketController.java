package com.example.stock.controller.market;

import com.example.stock.service.market.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @PostMapping("/api/balance/notifications")
    public void sendBalanceToTelegram() {
        marketService.sendBalanceToTelegram();
    }

}
