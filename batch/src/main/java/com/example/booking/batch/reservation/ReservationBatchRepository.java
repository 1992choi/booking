package com.example.booking.batch.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReservationBatchRepository extends JpaRepository<Reservation, Long> {

    @Modifying
    @Query("UPDATE Reservation r SET r.status = :cancelled WHERE r.status = :pending AND r.createdAt < :threshold")
    int expireReservations(
            @Param("pending") ReservationStatus pending,
            @Param("cancelled") ReservationStatus cancelled,
            @Param("threshold") LocalDateTime threshold
    );

}
