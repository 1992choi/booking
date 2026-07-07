package com.example.booking.review.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ReviewCreateRequest(

    @field:NotNull
    val reservationId: Long?,

    @field:NotBlank
    val content: String?

)
