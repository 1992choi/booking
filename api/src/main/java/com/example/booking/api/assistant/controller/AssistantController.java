package com.example.booking.api.assistant.controller;

import com.example.booking.api.assistant.LiteLlmClient;
import com.example.booking.api.assistant.dto.ChatRequest;
import com.example.booking.api.assistant.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AssistantController {

    private final LiteLlmClient liteLlmClient;

    @PostMapping("/api/v1/assistant/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String reply = liteLlmClient.chat(request.message(), request.history());

        return new ChatResponse(reply);
    }

}
