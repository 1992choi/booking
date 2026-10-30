package com.example.booking.api.assistant;

import com.example.booking.api.assistant.dto.ChatTurn;
import com.example.booking.api.assistant.dto.LlmMessage;
import com.example.booking.api.assistant.dto.ToolCall;
import com.example.booking.api.assistant.dto.ToolDefinition;
import com.example.booking.core.auth.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    LiteLlmClient liteLlmClient;

    @Mock
    AssistantTool fakeTool;

    AssistantService assistantService;

    @BeforeEach
    void setUp() {
        assistantService = new AssistantService(liteLlmClient, List.of(fakeTool));
    }

    @Test
    @DisplayName("시스템 프롬프트 + 이전 대화 + 새 메시지 순서로 메시지 목록을 구성한다")
    void buildMessages_ordersSystemPromptHistoryThenNewMessage() {
        List<ChatTurn> history = List.of(
                new ChatTurn("user", "이전 질문"),
                new ChatTurn("assistant", "이전 답변"));

        List<LlmMessage> messages = assistantService.buildMessages("새 질문", history);

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(1).role()).isEqualTo("user");
        assertThat(messages.get(1).content()).isEqualTo("이전 질문");
        assertThat(messages.get(2).role()).isEqualTo("assistant");
        assertThat(messages.get(3)).isEqualTo(LlmMessage.user("새 질문"));
    }

    @Test
    @DisplayName("이전 대화가 없으면 시스템 프롬프트 + 새 메시지만으로 구성된다")
    void buildMessages_withoutHistory() {
        List<LlmMessage> messages = assistantService.buildMessages("질문", null);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(1)).isEqualTo(LlmMessage.user("질문"));
    }

    @Test
    @DisplayName("tool 호출이 없으면 바로 답변을 반환한다")
    void chat_noToolCall_returnsDirectReply() {
        given(liteLlmClient.complete(any(), any())).willReturn(LlmMessage.assistant("답변입니다.", null));

        String reply = assistantService.chat("질문", null, new AssistantContext(1L, "jwt-token", Role.USER));

        assertThat(reply).isEqualTo("답변입니다.");
    }

    @Test
    @DisplayName("tool 호출이 있으면 실행하고 그 결과를 반영한 최종 답변을 반환한다")
    void chat_withToolCall_executesToolAndReturnsFinalReply() {
        given(fakeTool.name()).willReturn("get_my_reservations");
        given(fakeTool.definition())
                .willReturn(ToolDefinition.of("get_my_reservations", "설명", AssistantTool.noParameters()));
        given(fakeTool.execute(any(), any())).willReturn("예약 없음");

        ToolCall toolCall = new ToolCall("call_1", "function", new ToolCall.FunctionCall("get_my_reservations", "{}"));
        given(liteLlmClient.complete(any(), any()))
                .willReturn(LlmMessage.assistant(null, List.of(toolCall)))
                .willReturn(LlmMessage.assistant("최종 답변", null));

        AssistantContext context = new AssistantContext(1L, "jwt-token", Role.USER);
        String reply = assistantService.chat("내 예약 상태 알려줘", null, context);

        assertThat(reply).isEqualTo("최종 답변");
        verify(fakeTool).execute(eq(context), eq("{}"));
    }

    @Test
    @DisplayName("ADMIN 전용 tool은 USER에게는 제공되지 않는다")
    void availableTools_excludesAdminOnlyToolForUser() {
        given(fakeTool.adminOnly()).willReturn(true);

        List<AssistantTool> tools = assistantService.availableTools(new AssistantContext(1L, "jwt-token", Role.USER));

        assertThat(tools).isEmpty();
    }

    @Test
    @DisplayName("ADMIN 전용 tool은 ADMIN에게는 제공된다")
    void availableTools_includesAdminOnlyToolForAdmin() {
        given(fakeTool.adminOnly()).willReturn(true);

        List<AssistantTool> tools =
                assistantService.availableTools(new AssistantContext(1L, "jwt-token", Role.ADMIN));

        assertThat(tools).containsExactly(fakeTool);
    }

}
