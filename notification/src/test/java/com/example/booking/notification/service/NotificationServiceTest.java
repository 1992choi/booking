package com.example.booking.notification.service;

import com.example.booking.notification.domain.NotificationRepository;
import com.example.booking.notification.domain.NotificationType;
import com.example.booking.notification.user.domain.UserSync;
import com.example.booking.notification.user.domain.UserSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NotificationServiceTest {

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserSyncRepository userSyncRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userSyncRepository.save(UserSync.builder()
                .id(1L).name("홍길동").email("hong@example.com").phone("010-1234-5678").build());
    }

    @Test
    @DisplayName("같은 예약에 같은 타입 알림이 중복 수신되면 두 번째는 저장하지 않는다")
    void send_duplicateReservationAndType_skipsSecondInsert() {
        notificationService.send(1L, 10L, NotificationType.CONFIRMED);
        notificationService.send(1L, 10L, NotificationType.CONFIRMED);

        assertThat(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).hasSize(1);
    }

    @Test
    @DisplayName("같은 예약이라도 타입이 다르면 각각 저장한다")
    void send_sameReservationDifferentType_savesBoth() {
        notificationService.send(1L, 10L, NotificationType.CONFIRMED);
        notificationService.send(1L, 10L, NotificationType.CANCELLED);

        assertThat(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).hasSize(2);
    }

}