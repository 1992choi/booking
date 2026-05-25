package com.example.booking.reservation.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.resourceId = :resourceId
              AND r.status <> com.example.booking.reservation.domain.ReservationStatus.CANCELLED
              AND r.startTime < :end
              AND r.endTime > :start
            """)
    List<Reservation> findOverlapping(
            @Param("resourceId") Long resourceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    Page<Reservation> findByUserIdAndStatus(Long userId, ReservationStatus status, Pageable pageable);

    @Query("SELECT r FROM Reservation r WHERE r.startTime >= :from AND r.startTime < :to")
    List<Reservation> findByMonthRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Page<Reservation> findByResourceIdIn(List<Long> resourceIds, Pageable pageable);

    Page<Reservation> findByResourceIdInAndStatus(List<Long> resourceIds, ReservationStatus status, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(r.headCount), 0) FROM Reservation r
            WHERE r.availableTimeId = :availableTimeId
              AND r.status <> com.example.booking.reservation.domain.ReservationStatus.CANCELLED
            """)
    int sumHeadCountByAvailableTimeId(@Param("availableTimeId") Long availableTimeId);
}