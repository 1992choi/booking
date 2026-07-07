package com.example.booking.review.dto

import jakarta.validation.constraints.NotBlank

data class ReviewUpdateRequest(

    @field:NotBlank
    val content: String?

)
