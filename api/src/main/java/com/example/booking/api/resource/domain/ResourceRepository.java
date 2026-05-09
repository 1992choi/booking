package com.example.booking.api.resource.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findAllByOwnerId(Long ownerId);
}