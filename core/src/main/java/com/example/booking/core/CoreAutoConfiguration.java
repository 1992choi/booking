package com.example.booking.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CoreAutoConfiguration {

    @Bean
    public PingService pingService() {
        return new PingService();
    }
}