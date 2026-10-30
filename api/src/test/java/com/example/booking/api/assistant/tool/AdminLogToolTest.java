package com.example.booking.api.assistant.tool;

import com.example.booking.api.assistant.AssistantContext;
import com.example.booking.api.assistant.LokiLogClient;
import com.example.booking.core.auth.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminLogToolTest {

    @Mock
    LokiLogClient lokiLogClient;

    AdminLogTool adminLogTool;

    @Test
    @DisplayName("ADMIN 전용 tool이다")
    void adminOnly_isTrue() {
        adminLogTool = new AdminLogTool(lokiLogClient, new ObjectMapper());

        assertThat(adminLogTool.adminOnly()).isTrue();
    }

    @Test
    @DisplayName("arguments의 query/minutes를 그대로 LokiLogClient에 전달한다")
    void execute_passesQueryAndMinutesToClient() {
        adminLogTool = new AdminLogTool(lokiLogClient, new ObjectMapper());
        given(lokiLogClient.query("{app=\"payment\"} |= \"error\"", 30, 20)).willReturn("로그 3건");

        String result = adminLogTool.execute(
                new AssistantContext(1L, "jwt", Role.ADMIN),
                "{\"query\":\"{app=\\\"payment\\\"} |= \\\"error\\\"\",\"minutes\":30}");

        assertThat(result).isEqualTo("로그 3건");
        verify(lokiLogClient).query("{app=\"payment\"} |= \"error\"", 30, 20);
    }

}
