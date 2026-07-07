package com.example.booking.review.reservation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

@Entity
@Immutable
@Table(name = "reservations")
class ReservationView(

    @Id
    val id: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "resource_id", nullable = false)
    val resourceId: Long,

    @Column(nullable = false)
    val status: String

) {

    fun isConfirmed(): Boolean = status == "CONFIRMED"

}
