package com.example.booking.reservation.user.event;

import com.example.booking.reservation.user.domain.UserSync;
import com.example.booking.reservation.user.domain.UserSyncRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserSyncRepository userSyncRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.created", groupId = "reservation-group")
    public void onUserCreated(String message) {
        try {
            UserCreatedKafkaEvent event = objectMapper.readValue(message, UserCreatedKafkaEvent.class);
            userSyncRepository.save(UserSync.builder()
                    .id(event.userId())
                    .name(event.name())
                    .email(event.email())
                    .build());
            log.info("유저 동기화 완료 userId={}", event.userId());
        } catch (Exception e) {
            log.error("user.created 처리 실패: {}", message, e);
        }
    }
}
