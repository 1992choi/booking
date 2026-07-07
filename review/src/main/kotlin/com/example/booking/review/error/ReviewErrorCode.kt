package com.example.booking.review.error

import com.example.booking.core.error.ErrorCode
import org.springframework.http.HttpStatus

enum class ReviewErrorCode(
    private val status: HttpStatus,
    private val code: String,
    private val message: String
) : ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_001", "리뷰를 찾을 수 없습니다."),
    NOT_MY_REVIEW(HttpStatus.FORBIDDEN, "REVIEW_002", "본인 리뷰만 수정·삭제할 수 있습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "REVIEW_003", "이미 리뷰를 작성한 예약입니다."),
    RESERVATION_NOT_CONFIRMED(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_004", "예약이 확정(CONFIRMED) 상태가 아닙니다.");

    override fun status(): HttpStatus = status

    override fun code(): String = code

    override fun message(): String = message

}
