package com.example.booking.api.user.controller;

import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.dto.UserResponse;
import com.example.booking.api.user.dto.UserUpdateRequest;
import com.example.booking.api.user.service.UserService;
import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/v1/admin/users")
    public List<UserResponse> getUsers(@RequestParam(required = false) Role role) {
        return userService.getAll(role).stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/users/me")
    public UserResponse getMe(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = userService.getById(principal.userId());

        return UserResponse.from(user);
    }

    @PutMapping("/api/v1/users/me")
    public UserResponse updateMe(@AuthenticationPrincipal AuthPrincipal principal,
                                 @Valid @RequestBody UserUpdateRequest request) {
        return UserResponse.from(userService.update(principal.userId(), request));
    }

    @DeleteMapping("/api/v1/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal AuthPrincipal principal) {
        userService.delete(principal.userId());
    }

}
