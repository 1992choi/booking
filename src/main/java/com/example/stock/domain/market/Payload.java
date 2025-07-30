package com.example.stock.domain.market;

public record Payload(String access_token, String nonce, String[] currencies) {
}
