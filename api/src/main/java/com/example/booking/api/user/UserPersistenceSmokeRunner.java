package com.example.booking.api.user;

import com.example.booking.api.user.domain.Role;
import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class UserPersistenceSmokeRunner {

    private static final String SMOKE_EMAIL = "smoke@example.com";

    private final UserRepository userRepository;

    @Bean
    public ApplicationRunner runSmokeUserPersist() {
        return args -> {
            User user = userRepository.findByEmail(SMOKE_EMAIL)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .name("smoke")
                            .email(SMOKE_EMAIL)
                            .phone("000-0000-0000")
                            .password("placeholder")
                            .role(Role.USER)
                            .build()));

            log.info("[SMOKE] User persisted: id={}, email={}, createdAt={}, updatedAt={}",
                    user.getId(), user.getEmail(), user.getCreatedAt(), user.getUpdatedAt());
        };
    }
}