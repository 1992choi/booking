package com.example.booking.batch.tasklet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyMerchantStatsTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_SQL = """
            SELECT r.merchant_id,
                   COUNT(CASE WHEN res.status = 'CONFIRMED' THEN 1 END) AS confirmed_count,
                   COUNT(CASE WHEN res.status = 'CANCELLED' THEN 1 END) AS cancelled_count,
                   COALESCE(SUM(CASE WHEN res.status = 'CONFIRMED' THEN res.amount ELSE 0 END), 0) AS total_revenue
            FROM reservations res
            JOIN resources r ON res.resource_id = r.id
            WHERE DATE(res.start_time) = ?
            GROUP BY r.merchant_id
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO daily_merchant_stats (stat_date, merchant_id, confirmed_count, cancelled_count, total_revenue)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                confirmed_count = VALUES(confirmed_count),
                cancelled_count = VALUES(cancelled_count),
                total_revenue = VALUES(total_revenue)
            """;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate targetDate = LocalDate.now().minusDays(1);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_SQL, targetDate);

        for (Map<String, Object> row : rows) {
            jdbcTemplate.update(UPSERT_SQL,
                    targetDate,
                    row.get("merchant_id"),
                    row.get("confirmed_count"),
                    row.get("cancelled_count"),
                    row.get("total_revenue"));
        }

        log.info("일별 매출 집계 완료 date={}, 가맹점 수={}", targetDate, rows.size());

        return RepeatStatus.FINISHED;
    }

}
