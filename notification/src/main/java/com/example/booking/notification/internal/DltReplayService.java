package com.example.booking.notification.internal;

import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DltReplayService {

    // notification이 구독하는 토픽 중 순서가 중요하지 않은 것들만 — user.*/reservation.* 등 순서 보장이
    // 필요한 쪽(reservation/payment 서비스)은 재발행 시 순서가 다시 깨질 수 있어 대상에서 제외한다.
    private static final Set<String> REPLAYABLE_DLT_TOPICS = Set.of(
            "user.created-dlt",
            "user.updated-dlt",
            "user.deleted-dlt",
            "payment.completed-dlt",
            "reservation.cancelled-dlt",
            "reservation.reminder-dlt"
    );
    private static final String DLT_SUFFIX = "-dlt";
    private static final String REPLAY_GROUP_ID = "notification-dlt-replay";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);

    private final KafkaTemplate<String, String> retryKafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public int replay(String dltTopic) {
        if (!REPLAYABLE_DLT_TOPICS.contains(dltTopic)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        String originalTopic = dltTopic.substring(0, dltTopic.length() - DLT_SUFFIX.length());

        int replayedCount;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            List<TopicPartition> partitions = consumer.partitionsFor(dltTopic).stream()
                    .map(info -> new TopicPartition(dltTopic, info.partition()))
                    .toList();
            consumer.assign(partitions);

            // 재발행한 메시지가 다시 실패해 같은 DLT 토픽으로 돌아오는 것까지 쫓아가며 끝없이 재처리하지
            // 않도록, 호출 시점의 끝 오프셋을 스냅샷으로 고정해 그 범위까지만 처리한다.
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
            replayedCount = 0;

            while (!caughtUpToSnapshot(consumer, partitions, endOffsets)) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
                for (ConsumerRecord<String, String> record : records) {
                    TopicPartition partition = new TopicPartition(record.topic(), record.partition());
                    if (record.offset() >= endOffsets.get(partition)) {
                        continue;
                    }

                    retryKafkaTemplate.send(originalTopic, record.key(), record.value());
                    replayedCount++;
                    offsetsToCommit.put(partition, new OffsetAndMetadata(record.offset() + 1));
                }
            }

            if (!offsetsToCommit.isEmpty()) {
                consumer.commitSync(offsetsToCommit);
            }
        }

        log.info("DLT 재처리 완료 dltTopic={}, originalTopic={}, count={}", dltTopic, originalTopic, replayedCount);

        return replayedCount;
    }

    private boolean caughtUpToSnapshot(KafkaConsumer<String, String> consumer,
                                        List<TopicPartition> partitions,
                                        Map<TopicPartition, Long> endOffsets) {
        return partitions.stream().allMatch(tp -> consumer.position(tp) >= endOffsets.get(tp));
    }

    private Map<String, Object> consumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, REPLAY_GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return props;
    }

}
