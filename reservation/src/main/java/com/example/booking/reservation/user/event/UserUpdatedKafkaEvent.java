package com.example.booking.reservation.user.event;

public record UserUpdatedKafkaEvent(Long userId, String name, String email, String phone) {
}
