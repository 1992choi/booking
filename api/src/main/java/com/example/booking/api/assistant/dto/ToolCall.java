package com.example.booking.api.assistant.dto;

public record ToolCall(String id, String type, FunctionCall function) {

    public record FunctionCall(String name, String arguments) {

    }

}
