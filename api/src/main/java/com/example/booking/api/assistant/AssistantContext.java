package com.example.booking.api.assistant;

import com.example.booking.core.auth.Role;

public record AssistantContext(Long userId, String jwt, Role role) {

}
