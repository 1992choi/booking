package com.example.booking.api.assistant.dto;

import java.util.List;

public record LlmMessage(String role, String content, List<ToolCall> tool_calls, String tool_call_id) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null);
    }

    public static LlmMessage assistant(String content, List<ToolCall> toolCalls) {
        return new LlmMessage("assistant", content, toolCalls, null);
    }

    public static LlmMessage toolResult(String toolCallId, String content) {
        return new LlmMessage("tool", content, null, toolCallId);
    }

}
