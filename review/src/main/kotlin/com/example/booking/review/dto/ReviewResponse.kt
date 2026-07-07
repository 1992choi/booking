package com.example.booking.review.dto

import com.example.booking.review.domain.Review
import java.time.LocalDateTime

data class ReviewResponse(
    val id: Long,
    val reservationId: Long,
    val merchantId: Long,
    val userId: Long,
    val content: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {

    companion object {
        fun from(review: Review): ReviewResponse = ReviewResponse(
            id = review.id!!,
            reservationId = review.reservationId,
            merchantId = review.merchantId,
            userId = review.userId,
            content = review.content,
            createdAt = review.createdAt,
            updatedAt = review.updatedAt
        )
    }

}
