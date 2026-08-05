package com.example.booking.payment.user.event;

import com.example.booking.payment.user.domain.UserSync;
import com.example.booking.payment.user.domain.UserSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    UserSyncRepository userSyncRepository;

    ObjectMapper objectMapper = new ObjectMapper();

    UserEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserEventConsumer(userSyncRepository, objectMapper);
    }

    @Test
    @DisplayName("user.created 수신 시 유저 정보를 저장한다")
    void onUserCreated_savesUserSync() {
        String message = """
                {"userId":1,"name":"Hong","email":"hong@example.com","phone":"010-1111-1111"}
                """;

        consumer.onUserCreated(message);

        ArgumentCaptor<UserSync> captor = ArgumentCaptor.forClass(UserSync.class);
        verify(userSyncRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getName()).isEqualTo("Hong");
        assertThat(captor.getValue().getEmail()).isEqualTo("hong@example.com");
        assertThat(captor.getValue().getPhone()).isEqualTo("010-1111-1111");
    }

    @Test
    @DisplayName("user.created 메시지가 잘못된 형식이면 예외 없이 무시한다")
    void onUserCreated_malformedMessage_doesNotThrow() {
        consumer.onUserCreated("not-a-json");

        verify(userSyncRepository, never()).save(any());
    }

    @Test
    @DisplayName("user.updated 수신 시 기존 유저 정보를 갱신한다")
    void onUserUpdated_updatesExistingUserSync() {
        UserSync existing = UserSync.builder()
                .id(1L).name("Old").email("old@example.com").phone("010-0000-0000").build();
        given(userSyncRepository.findById(1L)).willReturn(Optional.of(existing));

        String message = """
                {"userId":1,"name":"New","email":"new@example.com","phone":"010-9999-9999"}
                """;

        consumer.onUserUpdated(message);

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getPhone()).isEqualTo("010-9999-9999");
        verify(userSyncRepository).save(existing);
    }

    @Test
    @DisplayName("user.updated 수신 시 동기화된 유저가 없으면 아무 것도 하지 않는다")
    void onUserUpdated_userNotFound_doesNothing() {
        given(userSyncRepository.findById(1L)).willReturn(Optional.empty());

        String message = """
                {"userId":1,"name":"New","email":"new@example.com","phone":"010-9999-9999"}
                """;

        consumer.onUserUpdated(message);

        verify(userSyncRepository, never()).save(any());
    }

    @Test
    @DisplayName("user.deleted 수신 시 유저 동기화 정보를 삭제한다")
    void onUserDeleted_deletesUserSync() {
        String message = """
                {"userId":1}
                """;

        consumer.onUserDeleted(message);

        verify(userSyncRepository).deleteById(1L);
    }

}
