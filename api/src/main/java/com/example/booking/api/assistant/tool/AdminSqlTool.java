package com.example.booking.api.assistant.tool;

import com.example.booking.api.assistant.AdminSqlClient;
import com.example.booking.api.assistant.AssistantContext;
import com.example.booking.api.assistant.AssistantTool;
import com.example.booking.api.assistant.dto.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminSqlTool implements AssistantTool {

    private static final String NAME = "run_sql";

    private final AdminSqlClient adminSqlClient;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean adminOnly() {
        return true;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.of(
                NAME,
                "DB에 읽기 전용 SELECT 쿼리를 실행한다. 서비스별로 DB가 분리돼 있으니 테이블명에 스키마를 "
                        + "반드시 포함할 것(예: db_reservation.reservations, db_payment.payments, db_api.users). "
                        + "ADMIN 전용, CS 상담 중 특정 사용자/예약/결제 조회에 사용.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "실행할 SELECT SQL")),
                        "required", List.of("query"),
                        "additionalProperties", false));
    }

    @Override
    public String execute(AssistantContext context, String argumentsJson) {
        Map<String, Object> arguments = objectMapper.readValue(argumentsJson, Map.class);

        return adminSqlClient.query((String) arguments.get("query"));
    }

}
