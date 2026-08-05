package com.example.booking.api.user.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    UserEventPublisher publisher;

    @Test
    @DisplayName("유저 생성 시 user.created 이벤트를 발행한다")
    void onUserCreated_publishesToKafka() {
        publisher.onUserCreated(new UserCreatedDomainEvent(1L, "Hong", "hong@example.com", "010-1111-1111"));

        verify(kafkaTemplate).send(eq("user.created"),
                eq(new UserCreatedKafkaEvent(1L, "Hong", "hong@example.com", "010-1111-1111")));
    }

    @Test
    @DisplayName("유저 정보 수정 시 user.updated 이벤트를 발행한다")
    void onUserUpdated_publishesToKafka() {
        publisher.onUserUpdated(new UserUpdatedDomainEvent(1L, "Hong2", "hong2@example.com", "010-2222-2222"));

        verify(kafkaTemplate).send(eq("user.updated"),
                eq(new UserUpdatedKafkaEvent(1L, "Hong2", "hong2@example.com", "010-2222-2222")));
    }

    @Test
    @DisplayName("유저 삭제 시 user.deleted 이벤트를 발행한다")
    void onUserDeleted_publishesToKafka() {
        publisher.onUserDeleted(new UserDeletedDomainEvent(1L));

        verify(kafkaTemplate).send(eq("user.deleted"), eq(new UserDeletedKafkaEvent(1L)));
    }

}
