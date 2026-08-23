package com.example.booking.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient notificationRestClient(
            RestClient.Builder restClientBuilder, @Value("${booking.notification.url}") String notificationUrl) {

        return restClientBuilder.baseUrl(notificationUrl).build();
    }

}