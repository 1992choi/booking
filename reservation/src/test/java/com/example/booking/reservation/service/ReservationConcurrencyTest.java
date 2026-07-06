package com.example.booking.reservation.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.reservation.domain.Reservation;
import com.example.booking.reservation.domain.ReservationRepository;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.dto.CreateReservationRequest;
import com.example.booking.reservation.resource.domain.AvailableTime;
import com.example.booking.reservation.resource.domain.AvailableTimeRepository;
import com.example.booking.reservation.resource.domain.AvailableTimeStatus;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.domain.ResourceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=40")
class ReservationConcurrencyTest {

    private static final int MAX_CAPACITY = 10;
    private static final int ATTEMPTS = 30;

    @Autowired
    ReservationService reservationService;

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    AvailableTimeRepository availableTimeRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    Resource resource;
    AvailableTime slot;

    @BeforeEach
    void setUp() {
        resource = resourceRepository.save(Resource.builder()
                .merchantId(10L)
                .name("레이스 컨디션 테스트 룸")
                .description("동시성 테스트용")
                .price(10000L)
                .maxCapacity(MAX_CAPACITY)
                .build());

        slot = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(LocalDateTime.of(2026, 6, 1, 14, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 15, 0))
                .status(AvailableTimeStatus.OPEN)
                .build());
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll(reservationsForResource());
        availableTimeRepository.delete(slot);
        resourceRepository.delete(resource);
    }

    private List<Reservation> reservationsForResource() {
        return reservationRepository.findAll().stream()
                .filter(r -> r.getResourceId().equals(resource.getId()) && r.getStatus() != ReservationStatus.CANCELLED)
                .toList();
    }

    @Test
    @DisplayName("동시 예약 요청이 정원을 초과해도 오버셀링이 발생하지 않는다")
    void concurrentCreate_neverExceedsCapacity() throws InterruptedException {
        CreateReservationRequest request = new CreateReservationRequest(resource.getId(), List.of(slot.getId()), 1);

        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ATTEMPTS);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < ATTEMPTS; i++) {
            long userId = 1000L + i;
            pool.submit(() -> {
                try {
                    start.await();
                    reservationService.create(userId, request);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // CONFLICT/LOCK_FAILED 는 정원 초과 시 기대되는 결과
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        List<Reservation> reservations = reservationsForResource();

        assertThat(successCount.get()).isEqualTo(MAX_CAPACITY);
        assertThat(reservations).hasSize(MAX_CAPACITY);
        assertThat(reservations.stream().mapToInt(Reservation::getHeadCount).sum()).isEqualTo(MAX_CAPACITY);
    }

}
