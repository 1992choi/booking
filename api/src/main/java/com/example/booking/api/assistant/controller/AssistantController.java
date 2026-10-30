package com.example.booking.api.assistant.controller;

import com.example.booking.api.assistant.AssistantContext;
import com.example.booking.api.assistant.AssistantService;
import com.example.booking.api.assistant.dto.ChatRequest;
import com.example.booking.api.assistant.dto.ChatResponse;
import com.example.booking.core.auth.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AssistantController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AssistantService assistantService;

    @PostMapping("/api/v1/assistant/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request,
                             @AuthenticationPrincipal AuthPrincipal principal,
                             HttpServletRequest httpRequest) {
        String jwt = httpRequest.getHeader("Authorization").substring(BEARER_PREFIX.length());
        AssistantContext context = new AssistantContext(principal.userId(), jwt, principal.role());
        String reply = assistantService.chat(request.message(), request.history(), context);

        return new ChatResponse(reply);
    }

}
