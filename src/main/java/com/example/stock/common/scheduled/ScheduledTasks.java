package com.example.stock.common.scheduled;

import com.example.stock.service.market.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ScheduledTasks {

    private final MarketService marketService;

    @Scheduled(cron = "0 0 */3 * * *")
    public void sendBalanceToTelegram() {
        marketService.sendBalanceToTelegram();
    }

    @Scheduled(cron = "0 0 */3 * * *")
    public void reportProfitAnalysis() {
        marketService.reportProfitAnalysis();
    }

    @Scheduled(cron = "0 * * * * *")
    public void executeBuy() {
        marketService.executeBuy();
    }

    @Scheduled(cron = "0 * * * * *")
    public void executeSell() {
        marketService.executeSell();
    }

}
