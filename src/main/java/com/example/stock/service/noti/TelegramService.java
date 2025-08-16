package com.example.stock.service.noti;

import com.example.stock.domain.market.MarketPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;

@Slf4j
@Service
public class TelegramService {

    @Value("${telegram.token}")
    private String TELEGRAM_TOKEN;

    @Value("${telegram.chat-id}")
    private String TELEGRAM_CHAT_ID;

    public void sendSimpleMessage(String message) {
        BufferedReader in = null;
        try {
            URL obj = new URL("https://api.telegram.org/bot" + TELEGRAM_TOKEN + "/sendmessage?chat_id=" + TELEGRAM_CHAT_ID + "&text=" + URLEncoder.encode(message, "UTF-8"));
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

    public void sendExecutionCompleted(MarketPrice marketPrice) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

        // Set price info.
        StringBuffer sb = new StringBuffer();
        sb.append("[매수 체결] ").append(marketPrice.getMarketCode()).append("\n\n");
        sb.append("금액: ").append(decimalFormat.format(marketPrice.getMarketPrice()));

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

    public void sendExecutionSellCompleted(String marketCode, BigDecimal currentPrice, BigDecimal boughtPrice) {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

        StringBuffer sb = new StringBuffer();
        sb.append("[매도 체결 ").append(marketCode).append("\n\n");
        sb.append("구매가: ").append(decimalFormat.format(boughtPrice)).append("\n");
        sb.append("현재가: ").append(decimalFormat.format(currentPrice));

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
