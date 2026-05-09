package com.example.booking.api.owner.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
