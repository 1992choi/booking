package com.example.booking.api.user.event;

public record UserCreatedKafkaEvent(Long userId, String name, String email, String phone) {}
