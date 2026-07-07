package com.example.booking.review.domain

import org.springframework.data.jpa.repository.JpaRepository

interface ReviewRepository : JpaRepository<Review, Long> {

    fun existsByReservationId(reservationId: Long): Boolean

    fun findAllByMerchantId(merchantId: Long): List<Review>

}
