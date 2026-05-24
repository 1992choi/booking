package com.example.booking.notification.user.event;

public record UserCreatedKafkaEvent(Long userId, String name, String email, String phone) {
}
