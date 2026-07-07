package com.example.booking.review.reservation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

@Entity
@Immutable
@Table(name = "resources")
class ResourceView(

    @Id
    val id: Long,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: Long

)
