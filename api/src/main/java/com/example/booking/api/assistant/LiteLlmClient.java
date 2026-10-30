package com.example.booking.api.assistant;

import com.example.booking.api.assistant.dto.LlmMessage;
import com.example.booking.api.assistant.dto.ToolDefinition;
import com.example.booking.api.error.ApiErrorCode;
import com.example.booking.core.error.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiteLlmClient {

    private final RestClient litellmRestClient;

    @Value("${booking.litellm.model:claude}")
    private String model;

    @Value("${booking.litellm.api-key}")
    private String apiKey;

    @Retry(name = "litellm")
    @CircuitBreaker(name = "litellm", fallbackMethod = "fallback")
    public LlmMessage complete(List<LlmMessage> messages, List<ToolDefinition> tools) {
        CompletionResponse response = litellmRestClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(new CompletionRequest(model, messages, tools.isEmpty() ? null : tools))
                .retrieve()
                .body(CompletionResponse.class);

        return response.choices().getFirst().message();
    }

    LlmMessage fallback(List<LlmMessage> messages, List<ToolDefinition> tools, Exception e) {
        log.warn("LiteLLM 서킷 오픈 — reason={}", e.getMessage());
        throw new BusinessException(ApiErrorCode.ASSISTANT_UNAVAILABLE);
    }

    private record CompletionRequest(String model, List<LlmMessage> messages, List<ToolDefinition> tools) {

    }

    private record CompletionResponse(List<Choice> choices) {

    }

    private record Choice(LlmMessage message) {

    }

}
