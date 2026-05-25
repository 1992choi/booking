package com.example.booking.notification.user.event;

public record UserUpdatedKafkaEvent(Long userId, String name, String email, String phone) {
}
