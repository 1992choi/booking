package com.example.booking.reservation.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepositoryCustom {

    List<Reservation> findOverlapping(Long resourceId, LocalDateTime start, LocalDateTime end);

    List<Reservation> findByMonthRange(LocalDateTime from, LocalDateTime to);

    int sumHeadCountByAvailableTimeId(Long availableTimeId);

    Page<Reservation> findByUser(Long userId, ReservationStatus status, Pageable pageable);

    Page<Reservation> findByResources(List<Long> resourceIds, ReservationStatus status, Pageable pageable);

}
