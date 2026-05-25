package com.example.booking.api.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserCreated(UserCreatedDomainEvent event) {
        kafkaTemplate.send("user.created",
                new UserCreatedKafkaEvent(event.userId(), event.name(), event.email(), event.phone()));
        log.info("user.created 발행 userId={}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserUpdated(UserUpdatedDomainEvent event) {
        kafkaTemplate.send("user.updated",
                new UserUpdatedKafkaEvent(event.userId(), event.name(), event.email(), event.phone()));
        log.info("user.updated 발행 userId={}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserDeleted(UserDeletedDomainEvent event) {
        kafkaTemplate.send("user.deleted", new UserDeletedKafkaEvent(event.userId()));
        log.info("user.deleted 발행 userId={}", event.userId());
    }
}
