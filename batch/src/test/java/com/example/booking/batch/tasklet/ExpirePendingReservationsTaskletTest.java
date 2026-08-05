package com.example.booking.batch.tasklet;

import com.example.booking.batch.reservation.ReservationBatchRepository;
import com.example.booking.batch.reservation.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpirePendingReservationsTaskletTest {

    @Mock
    ReservationBatchRepository reservationRepository;

    ExpirePendingReservationsTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new ExpirePendingReservationsTasklet(reservationRepository);
        ReflectionTestUtils.setField(tasklet, "pendingExpiryMinutes", 10);
    }

    @Test
    @DisplayName("설정된 만료 시간(분) 이전의 PENDING 예약을 CANCELLED로 만료 처리한다")
    void execute_expiresPendingReservationsBeforeThreshold() throws Exception {
        given(reservationRepository.expireReservations(eq(ReservationStatus.PENDING), eq(ReservationStatus.CANCELLED), any()))
                .willReturn(3);

        tasklet.execute(null, null);

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reservationRepository).expireReservations(eq(ReservationStatus.PENDING), eq(ReservationStatus.CANCELLED), thresholdCaptor.capture());

        LocalDateTime expectedThreshold = LocalDateTime.now().minusMinutes(10);
        assertThat(Duration.between(thresholdCaptor.getValue(), expectedThreshold).abs()).isLessThan(Duration.ofSeconds(5));
    }

}
