package com.example.booking.notification.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSyncRepository extends JpaRepository<UserSync, Long> {
}
