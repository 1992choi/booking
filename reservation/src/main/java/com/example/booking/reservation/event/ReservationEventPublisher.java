package com.example.booking.reservation.event;

import com.example.booking.reservation.resource.domain.AvailableTimeRepository;
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
public class ReservationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AvailableTimeRepository availableTimeRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationCreated(ReservationCreatedDomainEvent event) {
        try {
            availableTimeRepository.findById(event.availableTimeId())
                    .ifPresent(at -> {
                        at.block();
                        availableTimeRepository.save(at);
                    });
        } catch (Exception e) {
            log.error("슬롯 BLOCKED 처리 실패 availableTimeId={}", event.availableTimeId(), e);
        }
        kafkaTemplate.send("reservation.created",
                new ReservationCreatedKafkaEvent(
                        event.reservationId(),
                        event.userId(),
                        event.resourceId(),
                        event.amount()
                ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationCancelled(ReservationCancelledDomainEvent event) {
        try {
            availableTimeRepository.findById(event.availableTimeId())
                    .ifPresent(at -> {
                        at.release();
                        availableTimeRepository.save(at);
                    });
        } catch (Exception e) {
            log.error("슬롯 OPEN 복원 실패 availableTimeId={}", event.availableTimeId(), e);
        }
        kafkaTemplate.send("reservation.cancelled",
                new ReservationCancelledKafkaEvent(
                        event.reservationId(),
                        event.userId()
                ));
    }
}
