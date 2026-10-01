package com.example.booking.batch.tasklet;

import com.example.booking.batch.metrics.DailyMerchantStatsMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DailyMerchantStatsTaskletTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    DailyMerchantStatsMetrics metrics;

    DailyMerchantStatsTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new DailyMerchantStatsTasklet(jdbcTemplate, metrics);
    }

    @Test
    @DisplayName("전일 집계 결과를 merchant별로 daily_merchant_stats에 upsert하고 지표를 기록한다")
    void execute_upsertsStatsForYesterday() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Map<String, Object> row = Map.of(
                "merchant_id", 1L,
                "confirmed_count", 5L,
                "cancelled_count", 2L,
                "total_revenue", 150000L
        );
        given(jdbcTemplate.queryForList(anyString(), eq(yesterday))).willReturn(List.of(row));

        tasklet.execute(null, null);

        verify(jdbcTemplate).update(anyString(), eq(yesterday), eq(1L), eq(5L), eq(2L), eq(150000L));
        verify(metrics).record(1L, 5L, 2L, 150000L);
    }

    @Test
    @DisplayName("집계 대상이 없으면 upsert와 지표 기록을 실행하지 않는다")
    void execute_noRows_doesNotUpsert() throws Exception {
        given(jdbcTemplate.queryForList(anyString(), any(LocalDate.class))).willReturn(List.of());

        tasklet.execute(null, null);

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any());
        verify(metrics, never()).record(any(), anyLong(), anyLong(), anyLong());
    }

}
