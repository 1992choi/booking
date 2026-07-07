package com.example.booking.review.controller

import com.example.booking.core.auth.AuthPrincipal
import com.example.booking.review.dto.ReviewCreateRequest
import com.example.booking.review.dto.ReviewResponse
import com.example.booking.review.dto.ReviewUpdateRequest
import com.example.booking.review.service.ReviewService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ReviewController(
    private val reviewService: ReviewService
) {

    @PostMapping("/api/v1/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: ReviewCreateRequest
    ): ReviewResponse = ReviewResponse.from(reviewService.create(principal.userId(), request))

    @GetMapping("/api/v1/reviews")
    fun getByMerchant(@RequestParam merchantId: Long): List<ReviewResponse> =
        reviewService.getByMerchant(merchantId).map { ReviewResponse.from(it) }

    @PatchMapping("/api/v1/reviews/{reviewId}")
    fun update(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable reviewId: Long,
        @Valid @RequestBody request: ReviewUpdateRequest
    ): ReviewResponse = ReviewResponse.from(reviewService.update(principal.userId(), reviewId, request))

    @DeleteMapping("/api/v1/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable reviewId: Long
    ) = reviewService.delete(principal.userId(), reviewId)

}
