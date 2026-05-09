package com.example.booking.api.user.controller;

import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.dto.UserResponse;
import com.example.booking.api.user.service.UserService;
import com.example.booking.core.auth.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/v1/users/me")
    public UserResponse getMe(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = userService.getById(principal.userId());
        return UserResponse.from(user);
    }
}
