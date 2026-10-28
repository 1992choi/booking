package com.example.booking.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

// Kafka는 여기 포함하지 않는다 — Testcontainers의 네이티브 KafkaContainer가 이 환경에서 기동에 실패해서
// (docs/99-backlog.md의 "Testcontainers 도입" 진행 상태 참고) Kafka는 기존 docker-compose 인프라를 그대로 쓴다.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }

}
