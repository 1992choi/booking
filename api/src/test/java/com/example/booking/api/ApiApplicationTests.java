package com.example.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ApiApplicationTests {

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void contextLoads() {
    }
}
