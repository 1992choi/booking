package com.example.booking.payment.controller;

import com.example.booking.core.PingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PingController {

    private final PingService pingService;

    @GetMapping("/ping")
    public String ping() {
        return "[PAYMENT] " + pingService.ping();
    }
}