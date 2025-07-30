package com.example.stock.controller.market;

import com.example.stock.service.market.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @GetMapping("/balance")
    public String getBalance() {
        return marketService.getBalance();
    }

}
