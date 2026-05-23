package com.example.booking.reservation.merchant.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantRepository;
import com.example.booking.reservation.merchant.domain.MerchantType;
import com.example.booking.reservation.resource.domain.AvailableTime;
import com.example.booking.reservation.resource.domain.AvailableTimeRepository;
import com.example.booking.reservation.resource.domain.AvailableTimeStatus;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.domain.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class MerchantReservationControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MerchantRepository merchantRepository;

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    AvailableTimeRepository availableTimeRepository;

    @MockitoBean
    JwtVerifier jwtVerifier;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    MockMvc mockMvc;

    static final AtomicLong userIdSeq = new AtomicLong(300L);
    long userId;
    Merchant merchant;
    Resource resource;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();

        userId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(userId, Role.USER));

        merchant = merchantRepository.save(Merchant.builder()
                .userId(userId)
                .name("Test Merchant")
                .phone("02-0000-0000")
                .type(MerchantType.PENSION)
                .build());

        resource = resourceRepository.save(Resource.builder()
                .merchantId(merchant.getId())
                .name("별채 A")
                .description("2인실")
                .price(150000L)
                .maxCapacity(4)
                .build());
    }

    @Test
    void getMerchantReservations_emptyResources_returnsEmptyWithoutReservations() throws Exception {
        Merchant emptyMerchant = merchantRepository.save(Merchant.builder()
                .userId(userId)
                .name("Empty Merchant")
                .phone("02-0000-0001")
                .type(MerchantType.CLASS)
                .build());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", emptyMerchant.getId())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getMerchantReservations_noReservations() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getMerchantReservations_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void getMerchantReservations_forbidden_otherUser() throws Exception {
        long otherUserId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", merchant.getId())
                        .header("Authorization", "Bearer otherToken"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void getMerchantReservations_merchantNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}/reservations", 9999L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_006"));
    }

    private AvailableTime createSlot(LocalDateTime start, LocalDateTime end) {
        return availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resource.getId())
                .startTime(start)
                .endTime(end)
                .status(AvailableTimeStatus.OPEN)
                .build());
    }
}
