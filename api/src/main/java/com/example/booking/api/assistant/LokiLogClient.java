package com.example.booking.api.assistant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class LokiLogClient {

    private static final Pattern BUSINESS_EXCEPTION_PATTERN =
            Pattern.compile("code=(\\S+?), status=.+?, message=(.+?)\"");
    private static final Duration LOOKBACK = Duration.ofMinutes(15);

    private final RestClient lokiRestClient;

    public String findRecentReservationError(Long userId) {
        String query = "{app=\"reservation\", userId=\"" + userId + "\"} |= \"Business exception\"";
        List<String> lines = queryLines(query, LOOKBACK, 5);

        return extractError(lines).orElse("최근 15분 내 예약 실패 기록을 찾지 못했습니다.");
    }

    public String query(String logQlQuery, int minutes, int limit) {
        List<String> lines = queryLines(logQlQuery, Duration.ofMinutes(minutes), limit);

        return lines.isEmpty() ? "조회된 로그가 없습니다." : String.join("\n", lines);
    }

    private List<String> queryLines(String logQlQuery, Duration lookback, int limit) {
        Instant end = Instant.now();
        Instant start = end.minus(lookback);

        try {
            LokiQueryResponse response = lokiRestClient.get()
                    .uri("/loki/api/v1/query_range?query={query}&start={start}&end={end}"
                                    + "&limit={limit}&direction=backward",
                            logQlQuery, start.getEpochSecond() + "000000000",
                            end.getEpochSecond() + "000000000", limit)
                    .retrieve()
                    .body(LokiQueryResponse.class);

            return response.data().result().stream()
                    .flatMap(stream -> stream.values().stream())
                    .map(value -> value.get(1))
                    .toList();
        } catch (RestClientException e) {
            log.warn("Loki 로그 조회 실패 — reason={}", e.getMessage());

            return List.of();
        }
    }

    private Optional<String> extractError(List<String> lines) {
        return lines.stream()
                .map(BUSINESS_EXCEPTION_PATTERN::matcher)
                .filter(Matcher::find)
                .findFirst()
                .map(m -> "code=" + m.group(1) + ", 사유: " + m.group(2));
    }

    private record LokiQueryResponse(LokiData data) {

    }

    private record LokiData(List<LokiStream> result) {

    }

    private record LokiStream(Map<String, String> stream, List<List<String>> values) {

    }

}
