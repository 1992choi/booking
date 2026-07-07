package com.example.booking.review.domain

import com.example.booking.core.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "reviews")
class Review protected constructor(

    @Column(name = "reservation_id", nullable = false, unique = true)
    val reservationId: Long,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String

) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun update(content: String) {
        this.content = content
    }

    companion object {
        fun create(reservationId: Long, merchantId: Long, userId: Long, content: String): Review =
            Review(reservationId, merchantId, userId, content)
    }

}
