package com.example.booking.api.assistant;

import com.example.booking.api.assistant.dto.ChatTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LiteLlmClientTest {

    @Mock
    RestClient litellmRestClient;

    @InjectMocks
    LiteLlmClient liteLlmClient;

    @Test
    @DisplayName("시스템 프롬프트 + 이전 대화 + 새 메시지 순서로 메시지 목록을 구성한다")
    void buildMessages_ordersSystemPromptHistoryThenNewMessage() {
        List<ChatTurn> history = List.of(
                new ChatTurn("user", "이전 질문"),
                new ChatTurn("assistant", "이전 답변"));

        List<ChatTurn> messages = liteLlmClient.buildMessages("새 질문", history);

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(1)).isEqualTo(new ChatTurn("user", "이전 질문"));
        assertThat(messages.get(2)).isEqualTo(new ChatTurn("assistant", "이전 답변"));
        assertThat(messages.get(3)).isEqualTo(new ChatTurn("user", "새 질문"));
    }

    @Test
    @DisplayName("이전 대화가 없으면 시스템 프롬프트 + 새 메시지만으로 구성된다")
    void buildMessages_withoutHistory() {
        List<ChatTurn> messages = liteLlmClient.buildMessages("질문", null);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(1)).isEqualTo(new ChatTurn("user", "질문"));
    }

}
