package com.example.booking.review.service

import com.example.booking.core.error.BusinessException
import com.example.booking.core.error.CommonErrorCode
import com.example.booking.review.domain.Review
import com.example.booking.review.domain.ReviewRepository
import com.example.booking.review.dto.ReviewCreateRequest
import com.example.booking.review.dto.ReviewUpdateRequest
import com.example.booking.review.error.ReviewErrorCode
import com.example.booking.review.reservation.ReservationViewRepository
import com.example.booking.review.reservation.ResourceViewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val reservationViewRepository: ReservationViewRepository,
    private val resourceViewRepository: ResourceViewRepository
) {

    @Transactional
    fun create(userId: Long, request: ReviewCreateRequest): Review {
        val reservation = reservationViewRepository.findById(request.reservationId!!)
            .orElseThrow { BusinessException(CommonErrorCode.NOT_FOUND) }
        if (reservation.userId != userId) {
            throw BusinessException(CommonErrorCode.FORBIDDEN)
        }
        if (!reservation.isConfirmed()) {
            throw BusinessException(ReviewErrorCode.RESERVATION_NOT_CONFIRMED)
        }
        if (reviewRepository.existsByReservationId(reservation.id)) {
            throw BusinessException(ReviewErrorCode.ALREADY_REVIEWED)
        }

        val resource = resourceViewRepository.findById(reservation.resourceId)
            .orElseThrow { BusinessException(CommonErrorCode.NOT_FOUND) }

        val review = reviewRepository.save(
            Review.create(reservation.id, resource.merchantId, userId, request.content!!)
        )

        return review
    }

    @Transactional(readOnly = true)
    fun getByMerchant(merchantId: Long): List<Review> =
        reviewRepository.findAllByMerchantId(merchantId)

    @Transactional
    fun update(userId: Long, reviewId: Long, request: ReviewUpdateRequest): Review {
        val review = findOrThrow(reviewId)
        if (review.userId != userId) {
            throw BusinessException(ReviewErrorCode.NOT_MY_REVIEW)
        }

        review.update(request.content!!)

        return review
    }

    @Transactional
    fun delete(userId: Long, reviewId: Long) {
        val review = findOrThrow(reviewId)
        if (review.userId != userId) {
            throw BusinessException(ReviewErrorCode.NOT_MY_REVIEW)
        }

        reviewRepository.delete(review)
    }

    private fun findOrThrow(reviewId: Long): Review =
        reviewRepository.findById(reviewId)
            .orElseThrow { BusinessException(ReviewErrorCode.NOT_FOUND) }

}
