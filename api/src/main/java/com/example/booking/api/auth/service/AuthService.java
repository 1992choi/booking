package com.example.booking.api.auth.service;

import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.error.ApiErrorCode;
import com.example.booking.api.user.domain.Role;
import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import com.example.booking.core.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(SignupRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(ApiErrorCode.EMAIL_DUPLICATED);
        }

        return userRepository.save(User.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build());
    }
}