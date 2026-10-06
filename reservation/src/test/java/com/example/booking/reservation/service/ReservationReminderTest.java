package com.example.booking.reservation.service;

import com.example.booking.reservation.domain.Reservation;
import com.example.booking.reservation.domain.ReservationRepository;
import com.example.booking.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ReservationReminderTest {

    @Autowired
    ReservationService reservationService;

    @Autowired
    ReservationRepository reservationRepository;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    final List<Reservation> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll(created);
        created.clear();
    }

    private Reservation save(ReservationStatus status, LocalDateTime startTime) {
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .availableTimeId(1L)
                .userId(10L)
                .resourceId(1L)
                .resourceName("리마인더 테스트룸")
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .status(status)
                .headCount(1)
                .amount(10000L)
                .build());
        created.add(reservation);

        return reservation;
    }

    @Test
    @DisplayName("윈도우 내 CONFIRMED 예약은 리마인더 발송 대상이 되고 reminderSentAt이 채워진다")
    void sendReminders_confirmedWithinWindow_marksReminderSent() {
        Reservation target = save(ReservationStatus.CONFIRMED, LocalDateTime.now().plusMinutes(30));

        reservationService.sendReminders(60);

        Reservation updated = reservationRepository.findById(target.getId()).orElseThrow();
        assertThat(updated.getReminderSentAt()).isNotNull();
        verify(kafkaTemplate).send(eq("reservation.reminder"), any());
    }

    @Test
    @DisplayName("윈도우를 벗어난 CONFIRMED 예약은 대상에서 제외된다")
    void sendReminders_outsideWindow_notMarked() {
        Reservation target = save(ReservationStatus.CONFIRMED, LocalDateTime.now().plusMinutes(120));

        reservationService.sendReminders(60);

        Reservation updated = reservationRepository.findById(target.getId()).orElseThrow();
        assertThat(updated.getReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("PENDING 예약은 대상에서 제외된다")
    void sendReminders_pendingStatus_notMarked() {
        Reservation target = save(ReservationStatus.PENDING, LocalDateTime.now().plusMinutes(30));

        reservationService.sendReminders(60);

        Reservation updated = reservationRepository.findById(target.getId()).orElseThrow();
        assertThat(updated.getReminderSentAt()).isNull();
    }

}
