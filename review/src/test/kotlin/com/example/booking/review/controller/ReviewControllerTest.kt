package com.example.booking.review.controller

import com.example.booking.core.auth.AuthPrincipal
import com.example.booking.core.auth.JwtVerifier
import com.example.booking.core.auth.Role
import com.example.booking.review.domain.ReviewRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class ReviewControllerTest {

    @Autowired
    lateinit var wac: WebApplicationContext

    @Autowired
    lateinit var springSecurityFilterChain: FilterChainProxy

    @Autowired
    lateinit var reviewRepository: ReviewRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    lateinit var jwtVerifier: JwtVerifier

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val builder = MockMvcBuilders.webAppContextSetup(wac)
        builder.addFilters<DefaultMockMvcBuilder>(springSecurityFilterChain)
        mockMvc = builder.build()

        given(jwtVerifier.verify(any())).willReturn(AuthPrincipal(1L, Role.USER))
        reviewRepository.deleteAll()
        jdbcTemplate.update("DELETE FROM reservations")
        jdbcTemplate.update("DELETE FROM resources")
    }

    @AfterEach
    fun tearDown() {
        reviewRepository.deleteAll()
        jdbcTemplate.update("DELETE FROM reservations")
        jdbcTemplate.update("DELETE FROM resources")
    }

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
    @DisplayName("리뷰 작성 성공 시 201과 생성된 리뷰를 반환한다")
    fun create_success() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")

        mockMvc.perform(
            post("/api/v1/reviews")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": $reservationId, "content": "좋았어요"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reservationId").value(reservationId))
            .andExpect(jsonPath("$.merchantId").value(100))
            .andExpect(jsonPath("$.content").value("좋았어요"))
    }

    @Test
    @DisplayName("내용 없이 리뷰를 작성하면 400을 반환한다")
    fun create_blankContent_returnsBadRequest() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")

        mockMvc.perform(
            post("/api/v1/reviews")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": $reservationId, "content": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("비인증 요청으로 리뷰를 작성하면 401을 반환한다")
    fun create_unauthorized() {
        mockMvc.perform(
            post("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": 1, "content": "내용"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("CONFIRMED 상태가 아닌 예약에 리뷰를 작성하면 422를 반환한다")
    fun create_notConfirmed_returnsUnprocessableEntity() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "PENDING")

        mockMvc.perform(
            post("/api/v1/reviews")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": $reservationId, "content": "내용"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("REVIEW_004"))
    }

    @Test
    @DisplayName("업체별 리뷰 목록은 인증 없이 조회할 수 있다")
    fun getByMerchant_permitAll() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        mockMvc.perform(
            post("/api/v1/reviews")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": $reservationId, "content": "좋았어요"}""")
        ).andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/reviews").param("merchantId", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].merchantId").value(100))
            .andExpect(jsonPath("$[0].content").value("좋았어요"))
    }

    @Test
    @DisplayName("본인 리뷰 수정 성공")
    fun update_success() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        mockMvc.perform(
            post("/api/v1/reviews")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": $reservationId, "content": "원본"}""")
        ).andExpect(status().isCreated)
        val reviewId = reviewRepository.findAllByMerchantId(100L)[0].id!!

        mockMvc.perform(
            patch("/api/v1/reviews/{reviewId}", reviewId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content": "수정됨"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").value("수정됨"))
    }

    @Test
    @DisplayName("타인의 리뷰를 수정하면 403을 반환한다")
    fun update_notOwner_returnsForbidden() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        mockMvc.perform(
            post("/api/v1/reviews")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": $reservationId, "content": "원본"}""")
        ).andExpect(status().isCreated)
        val reviewId = reviewRepository.findAllByMerchantId(100L)[0].id!!

        given(jwtVerifier.verify(any())).willReturn(AuthPrincipal(2L, Role.USER))

        mockMvc.perform(
            patch("/api/v1/reviews/{reviewId}", reviewId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content": "수정 시도"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("REVIEW_002"))
    }

    @Test
    @DisplayName("존재하지 않는 리뷰를 수정하면 404를 반환한다")
    fun update_notFound_returnsNotFound() {
        mockMvc.perform(
            patch("/api/v1/reviews/{reviewId}", 999999L)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content": "내용"}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("REVIEW_001"))
    }

    @Test
    @DisplayName("본인 리뷰 삭제 성공 시 204를 반환한다")
    fun delete_success() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        mockMvc.perform(
            post("/api/v1/reviews")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": $reservationId, "content": "삭제될 리뷰"}""")
        ).andExpect(status().isCreated)
        val reviewId = reviewRepository.findAllByMerchantId(100L)[0].id!!

        mockMvc.perform(
            delete("/api/v1/reviews/{reviewId}", reviewId)
                .header("Authorization", "Bearer test-token")
        ).andExpect(status().isNoContent)
    }

    @Test
    @DisplayName("타인의 리뷰를 삭제하면 403을 반환한다")
    fun delete_notOwner_returnsForbidden() {
        val resourceId = insertResource(100L)
        val reservationId = insertReservation(1L, resourceId, "CONFIRMED")
        mockMvc.perform(
            post("/api/v1/reviews")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reservationId": $reservationId, "content": "삭제 대상"}""")
        ).andExpect(status().isCreated)
        val reviewId = reviewRepository.findAllByMerchantId(100L)[0].id!!

        given(jwtVerifier.verify(any())).willReturn(AuthPrincipal(2L, Role.USER))

        mockMvc.perform(
            delete("/api/v1/reviews/{reviewId}", reviewId)
                .header("Authorization", "Bearer test-token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("REVIEW_002"))
    }

}
