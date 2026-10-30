package com.example.booking.api.assistant.tool;

import com.example.booking.api.assistant.AssistantContext;
import com.example.booking.api.assistant.AssistantTool;
import com.example.booking.api.assistant.LokiLogClient;
import com.example.booking.api.assistant.dto.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminLogTool implements AssistantTool {

    private static final String NAME = "run_logql";

    private final LokiLogClient lokiLogClient;
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
                "Loki에 임의의 LogQL 쿼리를 실행해 로그를 조회한다(예: {app=\"payment\"} |= \"error\"). "
                        + "app 라벨은 api/reservation/payment/notification 중 하나. ADMIN 전용, "
                        + "CS 상담 중 범위 제한 없이 로그를 직접 조사할 때 사용.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "LogQL 쿼리"),
                                "minutes", Map.of("type", "integer", "description", "최근 몇 분까지 조회할지")),
                        "required", List.of("query", "minutes"),
                        "additionalProperties", false));
    }

    @Override
    public String execute(AssistantContext context, String argumentsJson) {
        Map<String, Object> arguments = objectMapper.readValue(argumentsJson, Map.class);
        String query = (String) arguments.get("query");
        int minutes = ((Number) arguments.get("minutes")).intValue();

        return lokiLogClient.query(query, minutes, 20);
    }

}
