package com.example.booking.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@AutoConfiguration
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
@EnableJpaAuditing
public class JpaAuditingAutoConfiguration {
}