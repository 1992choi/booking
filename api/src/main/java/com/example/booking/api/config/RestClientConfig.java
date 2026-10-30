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

        // LLM 응답(특히 로컬 Ollama 모델)은 수 초~수십 초가 걸릴 수 있어 read timeout을 넉넉히 둔다.
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(60));

        return restClientBuilder
                .baseUrl(litellmUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

}