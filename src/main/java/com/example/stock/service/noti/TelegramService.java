package com.example.stock.service.noti;

import com.example.stock.domain.market.MarketPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramService {

    private final String TELEGRAM_TOKEN = "-";
    private final String TELEGRAM_CHAT_ID = "-";

    /**
     * 체결 알리미
     */
    public void sendExecutionCompleted(List<MarketPrice> recentPrice) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

        recentPrice.sort(Comparator.comparing(MarketPrice::getMarketPrice));

        // Set price info.
        StringBuffer sb = new StringBuffer();
        sb.append("[체결 알림]").append("\n\n");
        sb.append("금액 변동: ").append(recentPrice.stream().map(marketPrice -> decimalFormat.format(marketPrice.getMarketPrice())).collect(Collectors.joining("  >  ")));

        // Set price change percentage.
        BigDecimal lastPrice = recentPrice.get(recentPrice.size() - 1).getMarketPrice();
        BigDecimal prevLastPrice = recentPrice.get(recentPrice.size() - 2).getMarketPrice();

        BigDecimal percentChange = lastPrice.subtract(prevLastPrice)
                .divide(prevLastPrice, 10, RoundingMode.HALF_EVEN) // 10자리까지 유지
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);

        sb.append("\n등락율: ").append(percentChange).append("%");

        BufferedReader in = null;
        try {
            URL obj = new URL("https://api.telegram.org/bot" + TELEGRAM_TOKEN + "/sendmessage?chat_id=" + TELEGRAM_CHAT_ID + "&text=" + URLEncoder.encode(sb.toString(), "UTF-8"));
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                log.info("line={}", line);
            }
        } catch (Exception e) {
            log.error("sendExecutionCompleted Err", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    /**
     * 체결 알리미
     */
    public void sendExecutionSellCompleted(BigDecimal currentPrice, BigDecimal boughtPrice) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

        StringBuffer sb = new StringBuffer();
        sb.append("[체결 알림]").append("\n\n");
        sb.append("구매가: ").append(decimalFormat.format(currentPrice)).append("\n");
        sb.append("현재가: ").append(decimalFormat.format(boughtPrice));

        BufferedReader in = null;
        try {
            URL obj = new URL("https://api.telegram.org/bot" + TELEGRAM_TOKEN + "/sendmessage?chat_id=" + TELEGRAM_CHAT_ID + "&text=" + URLEncoder.encode(sb.toString(), "UTF-8"));
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                log.info("line={}", line);
            }
        } catch (Exception e) {
            log.error("sendExecutionCompleted Err", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

}
