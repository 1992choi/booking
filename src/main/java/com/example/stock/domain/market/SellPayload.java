package com.example.stock.domain.market;

public record SellPayload(String access_token, String nonce, String quote_currency, String target_currency, String type, String side, String price, String qty, String amount, String limit_price, Boolean post_only, String trigger_price){

    public static SellPayload sellMarketOrder(String access_token, String nonce, String quote_currency, String target_currency, String qty) {
        return new SellPayload(access_token, nonce, quote_currency, target_currency, "MARKET", "SELL", null, qty, null, null, null, null);
    }

}
