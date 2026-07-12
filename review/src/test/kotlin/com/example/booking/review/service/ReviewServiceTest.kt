package com.example.booking.review.service

import com.example.booking.core.error.BusinessException
import com.example.booking.core.error.CommonErrorCode
import com.example.booking.review.domain.ReviewRepository
import com.example.booking.review.dto.ReviewCreateRequest
import com.example.booking.review.dto.ReviewUpdateRequest
import com.example.booking.review.error.ReviewErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class ReviewServiceTest {

    @Autowired
    lateinit var reviewService: ReviewService

    @Autowired
    lateinit var reviewRepository: ReviewRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private fun insertResource(merchantId: Long): Long {
        jdbcTemplate.update(
            "INSERT INTO resources (merchant_id, name, description, price, max_capacity, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
            merchantId, "테스트 리소스", "설명", 10000L, 10
        )

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    private fun insertReservation(userId: Long, resourceId: Long, status: String): Long {
        jdbcTemplate.update(
            "INSERT INTO reservations (available_time_id, user_id, resource_id, resource_name, start_time, end_time, status, head_count, amount, created_at, updated_at) " +
                "VALUES (1, ?, ?, ?, '2026-06-01 14:00:00', '2026-06-01 15:00:00', ?, 1, 10000, NOW(), NOW())",
            userId, resourceId, "테스트 리소스", status
        )

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    @Test
    @DisplayName("본인의 CONFIRMED 예약에 리뷰를 작성하면 성공한다")
    fun create_success() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")

        val review = reviewService.create(1L, ReviewCreateRequest(reservationId, "좋았어요"))

        assertThat(review.id).isNotNull()
        assertThat(review.reservationId).isEqualTo(reservationId)
        assertThat(review.merchantId).isEqualTo(100L)
        assertThat(review.userId).isEqualTo(1L)
        assertThat(review.content).isEqualTo("좋았어요")
    }

    @Test
    @DisplayName("존재하지 않는 예약에 리뷰를 작성하면 404를 반환한다")
    fun create_reservationNotFound_throws() {
        assertThatThrownBy { reviewService.create(1L, ReviewCreateRequest(999999L, "내용")) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ e -> assertThat((e as BusinessException).errorCode).isEqualTo(CommonErrorCode.NOT_FOUND) })
    }

    @Test
    @DisplayName("타인의 예약에 리뷰를 작성하면 403을 반환한다")
    fun create_notOwner_throwsForbidden() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")

        assertThatThrownBy { reviewService.create(2L, ReviewCreateRequest(reservationId, "내용")) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ e -> assertThat((e as BusinessException).errorCode).isEqualTo(CommonErrorCode.FORBIDDEN) })
    }

    @Test
    @DisplayName("CONFIRMED 상태가 아닌 예약에 리뷰를 작성하면 422를 반환한다")
    fun create_notConfirmed_throwsUnprocessable() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "PENDING")

        assertThatThrownBy { reviewService.create(1L, ReviewCreateRequest(reservationId, "내용")) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ e -> assertThat((e as BusinessException).errorCode).isEqualTo(ReviewErrorCode.RESERVATION_NOT_CONFIRMED) })
    }

    @Test
    @DisplayName("이미 리뷰를 작성한 예약에 다시 작성하면 409를 반환한다")
    fun create_alreadyReviewed_throwsConflict() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        reviewService.create(1L, ReviewCreateRequest(reservationId, "첫 리뷰"))

        assertThatThrownBy { reviewService.create(1L, ReviewCreateRequest(reservationId, "두번째 리뷰")) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ e -> assertThat((e as BusinessException).errorCode).isEqualTo(ReviewErrorCode.ALREADY_REVIEWED) })
    }

    @Test
    @DisplayName("업체별 리뷰 조회는 해당 업체의 리뷰만 반환한다")
    fun getByMerchant_returnsOnlyMatchingMerchant() {
        val resourceA = insertResource(100L)
        val resourceB = insertResource(200L)
        val reservationA = insertReservation(1L, resourceA, "CONFIRMED")
        val reservationB = insertReservation(1L, resourceB, "CONFIRMED")
        reviewService.create(1L, ReviewCreateRequest(reservationA, "A 리뷰"))
        reviewService.create(1L, ReviewCreateRequest(reservationB, "B 리뷰"))

        val reviews = reviewService.getByMerchant(100L)

        assertThat(reviews).hasSize(1)
        assertThat(reviews[0].merchantId).isEqualTo(100L)
        assertThat(reviews[0].content).isEqualTo("A 리뷰")
    }

    @Test
    @DisplayName("본인 리뷰를 수정하면 내용이 반영된다")
    fun update_success() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        val review = reviewService.create(1L, ReviewCreateRequest(reservationId, "원본 내용"))

        val updated = reviewService.update(1L, review.id!!, ReviewUpdateRequest("수정된 내용"))

        assertThat(updated.content).isEqualTo("수정된 내용")
    }

    @Test
    @DisplayName("존재하지 않는 리뷰를 수정하면 404를 반환한다")
    fun update_notFound_throws() {
        assertThatThrownBy { reviewService.update(1L, 999999L, ReviewUpdateRequest("내용")) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ e -> assertThat((e as BusinessException).errorCode).isEqualTo(ReviewErrorCode.NOT_FOUND) })
    }

    @Test
    @DisplayName("타인의 리뷰를 수정하면 403을 반환한다")
    fun update_notOwner_throws() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        val review = reviewService.create(1L, ReviewCreateRequest(reservationId, "원본 내용"))

        assertThatThrownBy { reviewService.update(2L, review.id!!, ReviewUpdateRequest("수정 시도")) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ e -> assertThat((e as BusinessException).errorCode).isEqualTo(ReviewErrorCode.NOT_MY_REVIEW) })
    }

    @Test
    @DisplayName("본인 리뷰를 삭제하면 조회되지 않는다")
    fun delete_success() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        val review = reviewService.create(1L, ReviewCreateRequest(reservationId, "삭제될 리뷰"))

        reviewService.delete(1L, review.id!!)

        assertThat(reviewRepository.findById(review.id!!)).isEmpty()
    }

    @Test
    @DisplayName("존재하지 않는 리뷰를 삭제하면 404를 반환한다")
    fun delete_notFound_throws() {
        assertThatThrownBy { reviewService.delete(1L, 999999L) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ e -> assertThat((e as BusinessException).errorCode).isEqualTo(ReviewErrorCode.NOT_FOUND) })
    }

    @Test
    @DisplayName("타인의 리뷰를 삭제하면 403을 반환한다")
    fun delete_notOwner_throws() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        val review = reviewService.create(1L, ReviewCreateRequest(reservationId, "삭제 대상"))

        assertThatThrownBy { reviewService.delete(2L, review.id!!) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ e -> assertThat((e as BusinessException).errorCode).isEqualTo(ReviewErrorCode.NOT_MY_REVIEW) })
    }

}
