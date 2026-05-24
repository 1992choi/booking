package com.example.booking.payment.user.event;

public record UserCreatedKafkaEvent(Long userId, String name, String email, String phone) {
}
