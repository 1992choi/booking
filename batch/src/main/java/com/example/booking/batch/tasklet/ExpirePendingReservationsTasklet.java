package com.example.booking.batch.tasklet;

import com.example.booking.batch.reservation.ReservationBatchRepository;
import com.example.booking.batch.reservation.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirePendingReservationsTasklet implements Tasklet {

    @Value("${booking.batch.pending-expiry-minutes}")
    private int pendingExpiryMinutes;

    private final ReservationBatchRepository reservationRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendingExpiryMinutes);
        int count = reservationRepository.expireReservations(ReservationStatus.PENDING, ReservationStatus.CANCELLED, threshold);
        log.info("만료 처리된 예약 수: {}", count);

        return RepeatStatus.FINISHED;
    }

}
