package com.example.booking.notification.internal;

import com.example.booking.core.error.BusinessException;
import com.example.booking.notification.TestcontainersConfiguration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DltReplayServiceTest {

    @Autowired
    DltReplayService dltReplayService;

    @MockitoBean
    KafkaTemplate<String, String> retryKafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    KafkaProducer<String, String> testProducer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        testProducer = new KafkaProducer<>(props);

        dltReplayService.replay("user.created-dlt"); // 이전 테스트/수동 테스트로 남아있을 수 있는 메시지 비우기
        clearInvocations(retryKafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        testProducer.close();
    }

    @Test
    @DisplayName("허용되지 않은(다른 서비스 소유) 토픽은 BAD_REQUEST")
    void replay_notWhitelistedTopic_throwsBadRequest() {
        assertThatThrownBy(() -> dltReplayService.replay("payment.failed-dlt"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("DLT 토픽의 메시지를 원본 토픽으로 재발행하고, 처리한 메시지 수를 반환한다")
    void replay_republishesMessagesToOriginalTopic() throws Exception {
        testProducer.send(new ProducerRecord<>("user.created-dlt", "key-1", "value-1")).get();
        testProducer.send(new ProducerRecord<>("user.created-dlt", "key-2", "value-2")).get();

        int replayed = dltReplayService.replay("user.created-dlt");

        assertThat(replayed).isEqualTo(2);
        verify(retryKafkaTemplate).send("user.created", "key-1", "value-1");
        verify(retryKafkaTemplate).send("user.created", "key-2", "value-2");
    }

    @Test
    @DisplayName("이미 재처리한 메시지는 다시 처리하지 않는다")
    void replay_doesNotReprocessAlreadyHandledMessages() throws Exception {
        testProducer.send(new ProducerRecord<>("user.created-dlt", "once", "only-once")).get();

        int first = dltReplayService.replay("user.created-dlt");
        int second = dltReplayService.replay("user.created-dlt");

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
    }

    @Test
    @DisplayName("재처리할 메시지가 없으면 0을 반환하고 재발행하지 않는다")
    void replay_noMessages_returnsZeroWithoutSending() {
        dltReplayService.replay("reservation.cancelled-dlt"); // 이전 테스트/수동 테스트로 남아있을 수 있는 메시지 비우기
        clearInvocations(retryKafkaTemplate);

        int replayed = dltReplayService.replay("reservation.cancelled-dlt");

        assertThat(replayed).isEqualTo(0);
        verify(retryKafkaTemplate, never()).send(eq("reservation.cancelled"), any(), any());
    }

}
