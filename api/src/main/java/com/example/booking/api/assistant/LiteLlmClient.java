package com.example.booking.api.assistant;

import com.example.booking.api.assistant.dto.ChatTurn;
import com.example.booking.api.error.ApiErrorCode;
import com.example.booking.core.error.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiteLlmClient {

    private static final String SYSTEM_PROMPT = loadSystemPrompt();

    private final RestClient litellmRestClient;

    @Value("${booking.litellm.model:claude}")
    private String model;

    @Value("${booking.litellm.api-key}")
    private String apiKey;

    @Retry(name = "litellm")
    @CircuitBreaker(name = "litellm", fallbackMethod = "fallback")
    public String chat(String message, List<ChatTurn> history) {
        CompletionResponse response = litellmRestClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(new CompletionRequest(model, buildMessages(message, history)))
                .retrieve()
                .body(CompletionResponse.class);

        return response.choices().getFirst().message().content();
    }

    List<ChatTurn> buildMessages(String message, List<ChatTurn> history) {
        List<ChatTurn> messages = new ArrayList<>();
        messages.add(new ChatTurn("system", SYSTEM_PROMPT));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new ChatTurn("user", message));

        return messages;
    }

    String fallback(String message, List<ChatTurn> history, Exception e) {
        log.warn("LiteLLM 서킷 오픈 — reason={}", e.getMessage());
        throw new BusinessException(ApiErrorCode.ASSISTANT_UNAVAILABLE);
    }

    private static String loadSystemPrompt() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("assistant/system-prompt.txt").getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record CompletionRequest(String model, List<ChatTurn> messages) {

    }

    private record CompletionResponse(List<Choice> choices) {

    }

    private record Choice(ChatTurn message) {

    }

}
