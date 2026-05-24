package com.example.booking.api.user.event;

public record UserCreatedDomainEvent(Long userId, String name, String email, String phone) {}
