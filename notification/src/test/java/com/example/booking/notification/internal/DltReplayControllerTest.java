package com.example.booking.notification.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class DltReplayControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @MockitoBean
    DltReplayService dltReplayService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("재처리 요청 시 토픽명과 처리 건수를 응답한다")
    void replay_success() throws Exception {
        given(dltReplayService.replay("user.created-dlt")).willReturn(3);

        mockMvc.perform(post("/api/v1/internal/dlt/{dltTopic}/replay", "user.created-dlt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dltTopic").value("user.created-dlt"))
                .andExpect(jsonPath("$.replayedCount").value(3));
    }

}
