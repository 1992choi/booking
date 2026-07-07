package com.example.booking.review.reservation

import org.springframework.data.repository.Repository
import java.util.Optional

interface ReservationViewRepository : Repository<ReservationView, Long> {

    fun findById(id: Long): Optional<ReservationView>

}
