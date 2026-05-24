package com.example.booking.reservation.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserSyncRepository extends JpaRepository<UserSync, Long> {
    List<UserSync> findAllByIdIn(Collection<Long> ids);
}
