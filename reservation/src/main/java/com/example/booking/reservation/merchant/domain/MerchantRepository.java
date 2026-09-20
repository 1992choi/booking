package com.example.booking.reservation.merchant.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    List<Merchant> findAllByUserId(Long userId);

    Page<Merchant> findAllByType(MerchantType type, Pageable pageable);
}
