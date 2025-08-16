package com.example.stock.service.market;

import com.example.stock.domain.market.MarketPrice;
import com.example.stock.domain.market.MarketPriceResponse;
import com.example.stock.domain.market.Payload;
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
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    @Value("${coin.access-key}")
    private String COIN_ACCESS_KEY;

    @Value("${coin.secret}")
    private String COIN_SECRET;

    private final List<String> COIN_SYMBOLS = List.of("BTC", "XRP", "ETH");

    private final MarketPriceRepository marketPriceRepository;

    private final TradeHistoryRepository tradeHistoryRepository;

    private final TelegramService telegramService;

    public void sendBalanceToTelegram() {
        ObjectMapper objectMapper = new ObjectMapper();
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        var payload = new Payload(COIN_ACCESS_KEY, UUID.randomUUID().toString(), COIN_SYMBOLS.toArray(new String[0]));
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
            log.info("balances: {}", balances);

            for (Map<String, Object> balance : balances) {
                String currency = (String) balance.get("currency");
                BigDecimal averagePrice = new BigDecimal(balance.get("average_price").toString());
                BigDecimal available = new BigDecimal(balance.get("available").toString());
                BigDecimal total = averagePrice.multiply(available).setScale(2, RoundingMode.HALF_UP);

                MarketPrice currentPrice = getMarketPrice(currency);

                BigDecimal currentValue = currentPrice.getMarketPrice()
                        .divide(averagePrice, 8, RoundingMode.HALF_UP)
                        .multiply(total);

                BigDecimal profitOrLoss = currentValue.subtract(total);

                BigDecimal rate = profitOrLoss.divide(total, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                telegramService.sendSimpleMessage(String.format(
                        "[%s]\n현재가 = %s\n평단가 = %s\n보유금액 = %s\n평가금액 = %s\n손익 = %s (%s%%)",
                        currency,
                        decimalFormat.format(currentPrice.getMarketPrice()),
                        decimalFormat.format(averagePrice),
                        decimalFormat.format(total),
                        decimalFormat.format(currentValue),
                        decimalFormat.format(profitOrLoss),
                        decimalFormat.format(rate)
                ));
            }
        } catch (InterruptedException | IOException e) {
            telegramService.sendSimpleMessage("잔액조회 오류");
            throw new RuntimeException(e);
        }
    }

    public void reportProfitAnalysis() {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

        for (String coinSymbol : COIN_SYMBOLS) {
            BigDecimal investment = BigDecimal.valueOf(500_000);
            BigDecimal totalProfit = BigDecimal.ZERO;

            List<TradeHistory> tradeHistories = tradeHistoryRepository.findByMarketCode(coinSymbol);

            for (TradeHistory history : tradeHistories) {
                BigDecimal buyPrice = history.getTradePrice();
                BigDecimal sellPrice;
                if (history.getIsSold()) {
                    sellPrice = history.getSoldPrice();
                } else {
                    MarketPrice marketPrice = getMarketPrice(coinSymbol);
                    sellPrice = marketPrice.getMarketPrice();
                }

                // 수익률 계산
                BigDecimal profitRate = sellPrice.subtract(buyPrice).divide(buyPrice, 10, RoundingMode.HALF_UP);

                // 개별 거래 손익 = 수익률 × 투자금
                BigDecimal profitAmount = investment.multiply(profitRate);

                // 누적
                totalProfit = totalProfit.add(profitAmount);
            }

            telegramService.sendSimpleMessage(String.format("[%s]\n손익 = %s", coinSymbol, decimalFormat.format(totalProfit)));
        }
    }

    @Transactional
    public void executeBuy() {
        for (String coinSymbol : COIN_SYMBOLS) {
            // Fetches the current market price and stores it in the database.
            MarketPrice marketPrice = getMarketPrice(coinSymbol);
            if (marketPrice == null) {
                return;
            }

            saveMarketPrice(marketPrice);

            // Get recent price
            List<MarketPrice> recentPrice = getRecentPrices(coinSymbol);

            // Determines whether the conditions for buying are met.
            if (isBuyConditionMet(recentPrice, coinSymbol)) {
                buy(recentPrice.getFirst());
                telegramService.sendExecutionCompleted(recentPrice.getFirst());
            }
        }
    }

    @Transactional
    public void executeSell() {
        for (String coinSymbol : COIN_SYMBOLS) {
            MarketPrice marketPrice = getMarketPrice(coinSymbol);
            if (marketPrice == null) {
                return;
            }

            sell(marketPrice);
        }
    }

    private MarketPrice getMarketPrice(String coinSymbol) {
        BufferedReader in = null;
        try {
            URL obj = new URL("https://api.coinone.co.kr/public/v2/trades/KRW/" + coinSymbol + "?size=100");
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
                    .marketCode(coinSymbol)
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
        return marketPriceRepository.findTop20ByMarketCodeOrderByCreatedAtDesc(marketCode);
    }

    private boolean isBuyConditionMet(List<MarketPrice> recentPrices, String coinSymbol) {
        log.info("recentPrices: {}", recentPrices);

        if (recentPrices == null || recentPrices.size() < 20) {
            return false;
        }

        // 매수 후 매도하지 않았으면 Skip
        boolean hasUnfinishedTrade = tradeHistoryRepository.existsByIsSoldFalseAndMarketCode(coinSymbol);
        if (hasUnfinishedTrade) {
            return false;
        }

        // 연속 상승 확인
        if (!isThreeConsecutiveIncreases(recentPrices)) {
            return false;
        }

        // 변동성 필터링
        if (!checkVolatility(recentPrices)) {
            return false;
        }

        // 이동평균선 전략
        if (checkMovingAverageStrategy(recentPrices)) {
            return true;
        }

        return false;
    }

    // 3분 연속 상승
    private boolean isThreeConsecutiveIncreases(List<MarketPrice> recentPrices) {
        for (int i = 0; i < recentPrices.size() - 1; i++) {
            if (recentPrices.get(i).getMarketPrice().compareTo(recentPrices.get(i + 1).getMarketPrice()) <= 0) {
                return false;
            }
        }
        return true;
    }

    // 변동성 필터 (최근 20개 가격 범위 1% 이상이어야 활성장)
    private boolean checkVolatility(List<MarketPrice> prices) {
        BigDecimal max = prices.stream()
                .map(MarketPrice::getMarketPrice)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal min = prices.stream()
                .map(MarketPrice::getMarketPrice)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        BigDecimal rangeRate = max.subtract(min).divide(min, 4, RoundingMode.HALF_UP);
        return rangeRate.compareTo(BigDecimal.valueOf(0.01)) >= 0; // 1% 이상 변동성 필요
    }

    // 이동평균선 계산
    private BigDecimal movingAverage(List<MarketPrice> prices, int period) {
        return prices.stream()
                .limit(period)
                .map(MarketPrice::getMarketPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), RoundingMode.HALF_UP);
    }

    // 이동평균선 전략 (단기 5, 장기 20)
    private boolean checkMovingAverageStrategy(List<MarketPrice> prices) {
        BigDecimal shortMA = movingAverage(prices, 5);
        BigDecimal longMA = movingAverage(prices, 20);
        BigDecimal current = prices.getFirst().getMarketPrice();

        return shortMA.compareTo(longMA) > 0 && current.compareTo(shortMA) > 0;
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
                .findTopByMarketCodeAndIsSoldFalseOrderByCreatedAtAsc(marketPrice.getMarketCode())
                .orElse(null);
        if (tradeHistory == null || Boolean.TRUE.equals(tradeHistory.getIsSold())) {
            return;
        }

        BigDecimal boughtPrice = tradeHistory.getTradePrice();
        if (currentPrice.compareTo(boughtPrice.multiply(BigDecimal.valueOf(1.012))) > 0 || currentPrice.compareTo(boughtPrice.multiply(BigDecimal.valueOf(0.95))) < 0) {
            tradeHistory.markAsSold(currentPrice);
            telegramService.sendExecutionSellCompleted(marketPrice.getMarketCode(), currentPrice, boughtPrice);
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
