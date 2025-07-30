package com.example.stock.service.market;

import com.example.stock.domain.market.Payload;
import com.example.stock.domain.market.MarketPrice;
import com.example.stock.domain.market.MarketPriceResponse;
import com.example.stock.domain.market.TradeHistory;
import com.example.stock.repository.market.MarketPriceRepository;
import com.example.stock.repository.market.TradeHistoryRepository;
import com.example.stock.service.noti.TelegramService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    @Value("${coin.access-key}")
    private String COIN_ACCESS_KEY;

    @Value("${coin.secret}")
    private String COIN_SECRET;

    private final MarketPriceRepository marketPriceRepository;

    private final TradeHistoryRepository tradeHistoryRepository;

    private final TelegramService telegramService;

    public String getBalance() {
        ObjectMapper objectMapper = new ObjectMapper();
        var payload = new Payload(COIN_ACCESS_KEY, UUID.randomUUID().toString(), new String[]{"BTC", "XRP"});
        var base64EncodedPayload = makeBase64EncodedPayload(payload);
        var signature = makeSignature(base64EncodedPayload);

        try {
            var client = HttpClient.newBuilder().build();
            var body = objectMapper.writeValueAsString(payload);
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.coinone.co.kr/v2.1/account/balance"))
                    .header("Content-type", "application/json")
                    .header("X-COINONE-PAYLOAD", base64EncodedPayload)
                    .header("X-COINONE-SIGNATURE", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("statusCode: {}", response.statusCode());

            Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> balances = (List<Map<String, Object>>) responseMap.get("balances");
            StringBuilder sb = new StringBuilder();

            for (Map<String, Object> balance : balances) {
                String currency = (String) balance.get("currency");
                BigDecimal averagePrice = new BigDecimal(balance.get("average_price").toString());
                BigDecimal available = new BigDecimal(balance.get("available").toString());
                BigDecimal total = averagePrice.multiply(available).setScale(2, RoundingMode.HALF_UP);

                sb.append(String.format("[%s] 평단가=%.0f, 잔액=%.2f%n", currency, averagePrice, total));
            }

            return sb.toString();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void executeBuy() {
        // Fetches the current market price and stores it in the database.
        MarketPrice marketPrice = getMarketPrice();
        if (marketPrice == null) {
            return;
        }

        saveMarketPrice(marketPrice);

        // Get recent price
        List<MarketPrice> recentPrice = getRecentPrices("BTC");

        // Determines whether the conditions for buying are met.
        if (recentPrice.size() > 2 && isBuyConditionMet(recentPrice)) {
            buy(recentPrice.getFirst());
            telegramService.sendExecutionCompleted(recentPrice);
        }
    }

    @Transactional
    public void executeSell() {
        MarketPrice marketPrice = getMarketPrice();
        if (marketPrice == null) {
            return;
        }

        sell(marketPrice);
    }

    private MarketPrice getMarketPrice() {
        BufferedReader in = null;
        try {
            URL obj = new URL("https://api.coinone.co.kr/public/v2/trades/KRW/BTC?size=100");
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            String resultString = in.readLine();
            ObjectMapper objectMapper = new ObjectMapper();
            MarketPriceResponse marketPriceResponse = objectMapper.readValue(resultString, MarketPriceResponse.class);

            BigDecimal averagePrice = marketPriceResponse.getTransactions().stream()
                    .map(transaction -> new BigDecimal(transaction.getPrice()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(marketPriceResponse.getTransactions().size()), BigDecimal.ROUND_HALF_UP);

            return MarketPrice.builder()
                    .marketCode("BTC")
                    .marketPrice(averagePrice)
                    .build();
        } catch (Exception e) {
            log.error("getMarketPrice Err", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        return null;
    }

    private void saveMarketPrice(MarketPrice marketPrice) {
        marketPriceRepository.save(marketPrice);
    }

    private List<MarketPrice> getRecentPrices(String marketCode) {
        return marketPriceRepository.findTop3ByMarketCodeOrderByCreatedAtDesc(marketCode);
    }

    private boolean isBuyConditionMet(List<MarketPrice> recentPrices) {
        log.info("recentPrices: {}", recentPrices);

        // 매수 후 매도하지 않았으면 Skip
        boolean hasUnfinishedTrade = tradeHistoryRepository.existsByIsSoldFalseAndMarketCode("BTC");
        if (hasUnfinishedTrade) {
            return false;
        }

        // TODO: 조건 수정 필요
        for (int i = 0; i < recentPrices.size() - 1; i++) {
            // 상승장인지 판단
            if (recentPrices.get(i).getMarketPrice().compareTo(recentPrices.get(i + 1).getMarketPrice()) < 0) {
                return false;
            }
        }

        return true;
    }

    private void buy(MarketPrice recentMarketPrice) {
        // TODO: 실제 매수

        // Set trade history.
        TradeHistory tradeHistory = TradeHistory.builder()
                .tradeDate(LocalDate.now())
                .marketCode(recentMarketPrice.getMarketCode())
                .tradePrice(recentMarketPrice.getMarketPrice())
                .build();

        tradeHistoryRepository.save(tradeHistory);
    }

    private void sell(MarketPrice marketPrice) {
        BigDecimal currentPrice = marketPrice.getMarketPrice();

        // TODO: 내 지갑에서 가져오도록 변경 필요.
        TradeHistory tradeHistory = tradeHistoryRepository
                .findTopByMarketCodeAndIsSoldFalseOrderByCreatedAtAsc("BTC")
                .orElse(null);
        if (tradeHistory == null || Boolean.TRUE.equals(tradeHistory.getIsSold())) {
            return;
        }

        BigDecimal boughtPrice = tradeHistory.getTradePrice();
        if (currentPrice.compareTo(boughtPrice.multiply(BigDecimal.valueOf(1.012))) > 0 || currentPrice.compareTo(boughtPrice.multiply(BigDecimal.valueOf(0.95))) < 0) {
            tradeHistory.markAsSold(currentPrice);
            telegramService.sendExecutionSellCompleted(currentPrice, boughtPrice);
        }
    }

    private String makeBase64EncodedPayload(Payload balancePayload) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            var bytesPayload = objectMapper.writeValueAsBytes(balancePayload);
            return Base64.getEncoder().encodeToString(bytesPayload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String makeSignature(String base64EncodedPayload) {
        try {
            var mac = Mac.getInstance("HmacSHA512");
            var keySpec = new SecretKeySpec(COIN_SECRET.getBytes(), "HmacSHA512");
            mac.init(keySpec);
            var messageDigest = mac.doFinal(base64EncodedPayload.getBytes());
            var sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

}
