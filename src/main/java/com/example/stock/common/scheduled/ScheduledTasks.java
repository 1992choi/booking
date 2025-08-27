package com.example.stock.common.scheduled;

import com.example.stock.service.market.MarketService;
import com.example.stock.service.noti.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ScheduledTasks {

    @Value("${batch.notify-enabled}")
    private boolean isNotifyEnabled;

    private final MarketService marketService;

    private final TelegramService telegramService;

//    @Scheduled(cron = "0 0 */3 * * *")
//    public void sendBalanceToTelegram() {
//        marketService.sendBalanceToTelegram();
//    }

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

    // @Scheduled(cron = "0 0/30 * * * *")
    @Scheduled(cron = "0 * * * * *")
    public void sendNotification() {
        if (isNotifyEnabled) {
            telegramService.sendSimpleMessage("Application is healthy.");
        }
    }

}
