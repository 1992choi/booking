package com.example.stock.domain.market;

public record BalancePayload(String access_token, String nonce, String[] currencies) {
}
