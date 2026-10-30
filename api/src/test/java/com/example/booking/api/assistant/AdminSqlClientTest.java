package com.example.booking.api.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminSqlClientTest {

    @Mock
    JdbcTemplate adminJdbcTemplate;

    @InjectMocks
    AdminSqlClient adminSqlClient;

    @Test
    @DisplayName("SELECT가 아닌 쿼리는 실행하지 않고 거부한다")
    void query_rejectsNonSelectStatements() {
        String result = adminSqlClient.query("DELETE FROM db_reservation.reservations");

        assertThat(result).isEqualTo("SELECT 쿼리만 실행할 수 있습니다.");
        verifyNoInteractions(adminJdbcTemplate);
    }

    @Test
    @DisplayName("SELECT 쿼리는 실행하고 결과를 반환한다")
    void query_executesSelectAndReturnsRows() {
        given(adminJdbcTemplate.queryForList("SELECT * FROM db_reservation.reservations WHERE id = 1"))
                .willReturn(List.of(Map.of("id", 1, "status", "CONFIRMED")));

        String result = adminSqlClient.query("SELECT * FROM db_reservation.reservations WHERE id = 1");

        assertThat(result).contains("id=1").contains("status=CONFIRMED");
    }

    @Test
    @DisplayName("결과가 없으면 안내 문구를 반환한다")
    void query_noRows_returnsEmptyMessage() {
        given(adminJdbcTemplate.queryForList("SELECT * FROM db_reservation.reservations WHERE id = 999"))
                .willReturn(List.of());

        String result = adminSqlClient.query("SELECT * FROM db_reservation.reservations WHERE id = 999");

        assertThat(result).isEqualTo("조회 결과가 없습니다.");
    }

}
