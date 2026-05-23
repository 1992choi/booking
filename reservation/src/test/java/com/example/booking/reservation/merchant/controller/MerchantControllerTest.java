package com.example.booking.reservation.merchant.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.merchant.dto.MerchantCreateRequest;
import com.example.booking.reservation.merchant.dto.MerchantUpdateRequest;
import com.example.booking.reservation.merchant.domain.MerchantType;
import com.example.booking.reservation.resource.dto.ResourceCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class MerchantControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    JwtVerifier jwtVerifier;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    MockMvc mockMvc;

    static final AtomicLong userIdSeq = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(userIdSeq.incrementAndGet(), Role.USER));
    }

    @Test
    void register_success() throws Exception {
        MerchantCreateRequest request = new MerchantCreateRequest("Sunset Pension", "02-1111-2222", MerchantType.PENSION);

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Sunset Pension"))
                .andExpect(jsonPath("$.phone").value("02-1111-2222"))
                .andExpect(jsonPath("$.type").value("PENSION"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void register_allows_multiple() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("First Pension", "02-1111-2222", MerchantType.PENSION))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Second Class", "02-3333-4444", MerchantType.CLASS))))
                .andExpect(status().isCreated());
    }

    @Test
    void register_unauthorized() throws Exception {
        MerchantCreateRequest request = new MerchantCreateRequest("No Auth", "02-9999-9999", MerchantType.PENSION);

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void getMyMerchants_success() throws Exception {
        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("My Class", "02-5555-6666", MerchantType.CLASS))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My Class"))
                .andExpect(jsonPath("$[0].type").value("CLASS"));
    }

    @Test
    void getMyMerchants_empty() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMerchants_success() throws Exception {
        long userId1 = userIdSeq.incrementAndGet();
        long userId2 = userIdSeq.incrementAndGet();

        given(jwtVerifier.verify(any()))
                .willReturn(new AuthPrincipal(userId1, Role.USER))
                .willReturn(new AuthPrincipal(userId2, Role.USER));

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Pension A", "02-1111-1111", MerchantType.PENSION))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Class B", "02-2222-2222", MerchantType.CLASS))))
                .andExpect(status().isCreated());

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(userId1, Role.USER));
        mockMvc.perform(get("/api/v1/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Pension A')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Class B')]").exists());
    }

    @Test
    void getMerchant_success() throws Exception {
        String merchantResponse = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Beach Pension", "02-7777-8888", MerchantType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long merchantId = objectMapper.readTree(merchantResponse).get("id").asLong();

        ResourceCreateRequest resource = new ResourceCreateRequest("별채 A", "2인실", 100000L, 2);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchantId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchantId))
                .andExpect(jsonPath("$.name").value("Beach Pension"))
                .andExpect(jsonPath("$.type").value("PENSION"))
                .andExpect(jsonPath("$.resources.length()").value(1))
                .andExpect(jsonPath("$.resources[0].name").value("별채 A"));
    }

    @Test
    void getMerchant_not_found() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_006"));
    }

    @Test
    void update_success() throws Exception {
        String response = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Original Name", "02-1111-1111", MerchantType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long merchantId = objectMapper.readTree(response).get("id").asLong();

        MerchantUpdateRequest update = new MerchantUpdateRequest("Updated Name", "02-9999-9999", MerchantType.CLASS);
        mockMvc.perform(put("/api/v1/merchants/{merchantId}", merchantId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("02-9999-9999"))
                .andExpect(jsonPath("$.type").value("CLASS"));
    }

    @Test
    void update_notFound() throws Exception {
        MerchantUpdateRequest update = new MerchantUpdateRequest("Name", "02-1111-1111", MerchantType.PENSION);
        mockMvc.perform(put("/api/v1/merchants/{merchantId}", 9999L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RSV_006"));
    }

    @Test
    void update_forbidden() throws Exception {
        long ownerUserId = userIdSeq.incrementAndGet();
        long otherUserId = userIdSeq.incrementAndGet();

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(ownerUserId, Role.USER));

        String response = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer ownerToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Other's Merchant", "02-1111-1111", MerchantType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long merchantId = objectMapper.readTree(response).get("id").asLong();

        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));
        MerchantUpdateRequest update = new MerchantUpdateRequest("Hijack", "02-0000-0000", MerchantType.CLASS);
        mockMvc.perform(put("/api/v1/merchants/{merchantId}", merchantId)
                        .header("Authorization", "Bearer otherToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_invalidInput_blankName() throws Exception {
        MerchantCreateRequest request = new MerchantCreateRequest("", "02-1111-2222", MerchantType.PENSION);

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }
}
