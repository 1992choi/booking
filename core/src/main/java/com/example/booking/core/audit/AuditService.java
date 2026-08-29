package com.example.booking.core.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private static final String COLLECTION = "audit_logs";

    private final MongoTemplate mongoTemplate;
    private final String serviceName;

    public void record(String action, Long userId, Map<String, Object> detail) {
        try {
            mongoTemplate.insert(new AuditLog(null, serviceName, action, userId, detail, LocalDateTime.now()), COLLECTION);
        } catch (Exception e) {
            log.warn("감사 로그 기록 실패 action={}, userId={}", action, userId, e);
        }
    }

}
