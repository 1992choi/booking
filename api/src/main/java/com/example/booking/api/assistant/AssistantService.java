package com.example.booking.api.assistant;

import com.example.booking.api.assistant.dto.ChatTurn;
import com.example.booking.api.assistant.dto.LlmMessage;
import com.example.booking.api.assistant.dto.ToolCall;
import com.example.booking.api.assistant.dto.ToolDefinition;
import com.example.booking.core.auth.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final int MAX_TOOL_ITERATIONS = 3;
    private static final String SYSTEM_PROMPT = loadSystemPrompt();

    private final LiteLlmClient liteLlmClient;
    private final List<AssistantTool> tools;

    public String chat(String message, List<ChatTurn> history, AssistantContext context) {
        List<LlmMessage> messages = buildMessages(message, history);
        List<AssistantTool> availableTools = availableTools(context);
        List<ToolDefinition> toolDefinitions = availableTools.stream().map(AssistantTool::definition).toList();

        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            LlmMessage response = liteLlmClient.complete(messages, toolDefinitions);

            if (response.tool_calls() == null || response.tool_calls().isEmpty()) {
                return response.content();
            }

            messages.add(response);
            for (ToolCall toolCall : response.tool_calls()) {
                messages.add(LlmMessage.toolResult(toolCall.id(), executeTool(availableTools, toolCall, context)));
            }
        }

        return liteLlmClient.complete(messages, List.of()).content();
    }

    List<AssistantTool> availableTools(AssistantContext context) {
        return tools.stream()
                .filter(tool -> !tool.adminOnly() || context.role() == Role.ADMIN)
                .toList();
    }

    List<LlmMessage> buildMessages(String message, List<ChatTurn> history) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(SYSTEM_PROMPT));
        if (history != null) {
            for (ChatTurn turn : history) {
                messages.add(new LlmMessage(turn.role(), turn.content(), null, null));
            }
        }
        messages.add(LlmMessage.user(message));

        return messages;
    }

    private String executeTool(List<AssistantTool> availableTools, ToolCall toolCall, AssistantContext context) {
        return availableTools.stream()
                .filter(tool -> tool.name().equals(toolCall.function().name()))
                .findFirst()
                .map(tool -> tool.execute(context, toolCall.function().arguments()))
                .orElse("알 수 없는 tool 호출입니다.");
    }

    private static String loadSystemPrompt() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("assistant/system-prompt.txt").getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
