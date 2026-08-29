package com.example.booking.core.audit;

import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditLog(

        @Id
        String id,

        String service,
        String action,
        Long userId,
        Map<String, Object> detail,
        LocalDateTime createdAt

) {
}
