package com.example.booking.payment.user.event;

import com.example.booking.payment.user.domain.UserSync;
import com.example.booking.payment.user.domain.UserSyncRepository;
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

    @KafkaListener(topics = "user.created", groupId = "payment-group")
    public void onUserCreated(String message) {
        try {
            UserCreatedKafkaEvent event = objectMapper.readValue(message, UserCreatedKafkaEvent.class);

            userSyncRepository.save(UserSync.builder()
                    .id(event.userId())
                    .name(event.name())
                    .email(event.email())
                    .phone(event.phone())
                    .build());
            log.info("유저 동기화 완료 userId={}", event.userId());
        } catch (Exception e) {
            log.error("user.created 처리 실패: {}", message, e);
        }
    }

    @KafkaListener(topics = "user.updated", groupId = "payment-group")
    public void onUserUpdated(String message) {
        try {
            UserUpdatedKafkaEvent event = objectMapper.readValue(message, UserUpdatedKafkaEvent.class);

            userSyncRepository.findById(event.userId()).ifPresent(user -> {
                user.update(event.name(), event.email(), event.phone());
                userSyncRepository.save(user);
            });
            log.info("유저 정보 업데이트 완료 userId={}", event.userId());
        } catch (Exception e) {
            log.error("user.updated 처리 실패: {}", message, e);
        }
    }

    @KafkaListener(topics = "user.deleted", groupId = "payment-group")
    public void onUserDeleted(String message) {
        try {
            UserDeletedKafkaEvent event = objectMapper.readValue(message, UserDeletedKafkaEvent.class);

            userSyncRepository.deleteById(event.userId());
            log.info("유저 삭제 완료 userId={}", event.userId());
        } catch (Exception e) {
            log.error("user.deleted 처리 실패: {}", message, e);
        }
    }

}
