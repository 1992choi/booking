package com.example.booking.reservation.user.event;

public record UserCreatedKafkaEvent(Long userId, String name, String email, String phone) {}
