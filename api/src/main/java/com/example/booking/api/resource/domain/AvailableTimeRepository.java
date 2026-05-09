package com.example.booking.api.resource.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AvailableTimeRepository extends JpaRepository<AvailableTime, Long> {

    List<AvailableTime> findAllByResourceIdAndStartTimeBetween(
            Long resourceId, LocalDateTime from, LocalDateTime to);
}