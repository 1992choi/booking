package com.example.booking.payment.user.event;

public record UserUpdatedKafkaEvent(Long userId, String name, String email, String phone) {
}
