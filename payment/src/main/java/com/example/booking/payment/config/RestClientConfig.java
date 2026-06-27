package com.example.booking.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient pgRestClient(@Value("${booking.pg.url}") String pgUrl) {
        return RestClient.builder().baseUrl(pgUrl).build();
    }

}