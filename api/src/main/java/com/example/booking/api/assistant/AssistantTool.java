package com.example.booking.api.assistant;

import com.example.booking.api.assistant.dto.ToolDefinition;

import java.util.Map;

public interface AssistantTool {

    String name();

    ToolDefinition definition();

    String execute(AssistantContext context, String argumentsJson);

    default boolean adminOnly() {
        return false;
    }

    static Map<String, Object> noParameters() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }

}
