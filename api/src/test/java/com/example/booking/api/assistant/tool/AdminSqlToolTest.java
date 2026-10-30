package com.example.booking.api.assistant.tool;

import com.example.booking.api.assistant.AdminSqlClient;
import com.example.booking.api.assistant.AssistantContext;
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
class AdminSqlToolTest {

    @Mock
    AdminSqlClient adminSqlClient;

    AdminSqlTool adminSqlTool;

    @Test
    @DisplayName("ADMIN 전용 tool이다")
    void adminOnly_isTrue() {
        adminSqlTool = new AdminSqlTool(adminSqlClient, new ObjectMapper());

        assertThat(adminSqlTool.adminOnly()).isTrue();
    }

    @Test
    @DisplayName("arguments의 query를 그대로 AdminSqlClient에 전달한다")
    void execute_passesQueryToClient() {
        adminSqlTool = new AdminSqlTool(adminSqlClient, new ObjectMapper());
        given(adminSqlClient.query("SELECT * FROM db_reservation.reservations WHERE id = 1"))
                .willReturn("id=1, status=CONFIRMED");

        String result = adminSqlTool.execute(
                new AssistantContext(1L, "jwt", Role.ADMIN),
                "{\"query\":\"SELECT * FROM db_reservation.reservations WHERE id = 1\"}");

        assertThat(result).isEqualTo("id=1, status=CONFIRMED");
        verify(adminSqlClient).query("SELECT * FROM db_reservation.reservations WHERE id = 1");
    }

}
