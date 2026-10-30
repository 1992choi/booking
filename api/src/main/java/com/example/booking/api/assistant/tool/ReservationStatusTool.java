package com.example.booking.api.assistant.tool;

import com.example.booking.api.assistant.AssistantContext;
import com.example.booking.api.assistant.AssistantTool;
import com.example.booking.api.assistant.ReservationClient;
import com.example.booking.api.assistant.dto.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationStatusTool implements AssistantTool {

    private static final String NAME = "get_my_reservations";

    private final ReservationClient reservationClient;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.of(
                NAME,
                "현재 로그인한 사용자의 예약 목록(상태, 일시, 금액 포함)을 조회한다. 파라미터 없음.",
                AssistantTool.noParameters());
    }

    @Override
    public String execute(AssistantContext context, String argumentsJson) {
        return reservationClient.getMyReservations(context.jwt());
    }

}
