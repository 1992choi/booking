package com.example.booking.payment.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSyncRepository extends JpaRepository<UserSync, Long> {
}
