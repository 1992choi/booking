package com.example.booking.api.user.event;

public record UserUpdatedDomainEvent(Long userId, String name, String email, String phone) {
}
