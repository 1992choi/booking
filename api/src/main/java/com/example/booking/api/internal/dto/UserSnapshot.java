package com.example.booking.api.internal.dto;

import com.example.booking.api.user.domain.User;

public record UserSnapshot(
        Long id,
        String name,
        String email
) {

    public static UserSnapshot from(User user) {
        return new UserSnapshot(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}