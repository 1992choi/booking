package com.example.booking.review.reservation

import org.springframework.data.repository.Repository
import java.util.Optional

interface ResourceViewRepository : Repository<ResourceView, Long> {

    fun findById(id: Long): Optional<ResourceView>

}
