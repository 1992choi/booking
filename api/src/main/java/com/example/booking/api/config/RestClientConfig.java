package com.example.booking.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient notificationRestClient(
            RestClient.Builder restClientBuilder, @Value("${booking.notification.url}") String notificationUrl) {

        return restClientBuilder.baseUrl(notificationUrl).build();
    }

    @Bean
    public RestClient litellmRestClient(
            RestClient.Builder restClientBuilder, @Value("${booking.litellm.url}") String litellmUrl) {

        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(60));

        return restClientBuilder
                .baseUrl(litellmUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Bean
    public RestClient reservationRestClient(
            RestClient.Builder restClientBuilder, @Value("${booking.reservation.url}") String reservationUrl) {

        return restClientBuilder.baseUrl(reservationUrl).build();
    }

    @Bean
    public RestClient lokiRestClient(
            RestClient.Builder restClientBuilder, @Value("${booking.loki.url}") String lokiUrl) {

        return restClientBuilder.baseUrl(lokiUrl).build();
    }

}