package com.example.booking.api.assistant.tool;

import com.example.booking.api.assistant.AssistantContext;
import com.example.booking.api.assistant.AssistantTool;
import com.example.booking.api.assistant.LokiLogClient;
import com.example.booking.api.assistant.dto.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationErrorTool implements AssistantTool {

    private static final String NAME = "find_recent_reservation_error";

    private final LokiLogClient lokiLogClient;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.of(
                NAME,
                "현재 로그인한 사용자의 최근 15분 내 예약 생성 실패 원인을 조회한다. "
                        + "사용자가 예약이 안 됐다고 말할 때만 사용. 파라미터 없음.",
                AssistantTool.noParameters());
    }

    @Override
    public String execute(AssistantContext context, String argumentsJson) {
        return lokiLogClient.findRecentReservationError(context.userId());
    }

}
