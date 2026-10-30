package com.example.booking.api.assistant.dto;

import java.util.Map;

public record ToolDefinition(String type, FunctionDefinition function) {

    public static ToolDefinition of(String name, String description, Map<String, Object> parameters) {
        return new ToolDefinition("function", new FunctionDefinition(name, description, parameters));
    }

    public record FunctionDefinition(String name, String description, Map<String, Object> parameters) {

    }

}
