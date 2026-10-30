package com.example.booking.api.assistant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSqlClient {

    private static final Pattern SELECT_ONLY = Pattern.compile("^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate adminJdbcTemplate;

    public String query(String sql) {
        if (sql == null || !SELECT_ONLY.matcher(sql).find()) {
            return "SELECT 쿼리만 실행할 수 있습니다.";
        }

        try {
            List<Map<String, Object>> rows = adminJdbcTemplate.queryForList(sql);

            return rows.isEmpty()
                    ? "조회 결과가 없습니다."
                    : rows.stream().map(Object::toString).collect(Collectors.joining("\n"));
        } catch (DataAccessException e) {
            log.warn("admin SQL 실행 실패 — reason={}", e.getMessage());

            return "쿼리 실행에 실패했습니다: " + e.getMessage();
        }
    }

}
