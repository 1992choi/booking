package com.example.booking.reservation.resource.controller;

import com.example.booking.core.auth.AuthPrincipal;
import com.example.booking.core.auth.JwtVerifier;
import com.example.booking.core.auth.Role;
import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantRepository;
import com.example.booking.reservation.merchant.domain.MerchantType;
import com.example.booking.reservation.resource.dto.AvailableTimeCreateRequest;
import com.example.booking.reservation.resource.dto.AvailableTimeUpdateRequest;
import com.example.booking.reservation.resource.dto.ResourceCreateRequest;
import com.example.booking.reservation.resource.dto.ResourceUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ResourceControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MerchantRepository merchantRepository;

    @MockitoBean
    JwtVerifier jwtVerifier;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    MockMvc mockMvc;

    static final AtomicLong userIdSeq = new AtomicLong(200L);
    long userId;
    Merchant merchant;

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
    }

    @Test
    @DisplayName("리소스 등록 성공 시 201 및 필드 반환")
    void registerResource_success() throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest("별채 A", "2인실 독채", 150000L, 2);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchant.getId())
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.merchantId").value(merchant.getId()))
                .andExpect(jsonPath("$.name").value("별채 A"))
                .andExpect(jsonPath("$.price").value(150000))
                .andExpect(jsonPath("$.maxCapacity").value(2));
    }

    @Test
    @DisplayName("존재하지 않는 Merchant에 리소스 등록 시 404 반환")
    void registerResource_merchantNotFound() throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest("별채 A", "설명", 100000L, 2);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", 9999L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_006"));
    }

    @Test
    @DisplayName("비인증 요청으로 리소스 등록 시 401 반환")
    void registerResource_unauthorized() throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest("별채 A", "설명", 100000L, 2);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("가용 시간 추가 성공 시 201 및 OPEN 상태 반환")
    void addAvailableTime_success() throws Exception {
        Long resourceId = registerResource("별채 B", 100000L);

        AvailableTimeCreateRequest request = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0));

        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.startTime").value("2026-06-01T14:00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-06-01T15:00:00"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("존재하지 않는 리소스에 가용 시간 추가 시 404 반환")
    void addAvailableTime_resourceNotFound() throws Exception {
        AvailableTimeCreateRequest request = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 6, 1, 14, 0),
                LocalDateTime.of(2026, 6, 1, 15, 0));

        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", 9999L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_007"));
    }

    @Test
    @DisplayName("날짜로 가용 시간 목록 조회 성공")
    void getAvailableTimes_success() throws Exception {
        Long resourceId = registerResource("강의실 A", 50000L);

        AvailableTimeCreateRequest t1 = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 6, 10, 10, 0),
                LocalDateTime.of(2026, 6, 10, 11, 0));
        AvailableTimeCreateRequest t2 = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 6, 10, 14, 0),
                LocalDateTime.of(2026, 6, 10, 15, 0));

        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .param("date", "2026-06-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    @DisplayName("존재하지 않는 리소스의 가용 시간 조회 시 404 반환")
    void getAvailableTimes_resourceNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/resources/{resourceId}/available-times", 9999L)
                        .param("date", "2026-06-10"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_007"));
    }

    @Test
    @DisplayName("리소스 수정 성공 시 변경된 필드 반환")
    void updateResource_success() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);

        ResourceUpdateRequest update = new ResourceUpdateRequest("별채 B", "리모델링 완료", 200000L, 4);
        mockMvc.perform(put("/api/v1/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("별채 B"))
                .andExpect(jsonPath("$.price").value(200000))
                .andExpect(jsonPath("$.maxCapacity").value(4));
    }

    @Test
    @DisplayName("다른 유저가 리소스 수정 시 403 반환")
    void updateResource_forbidden() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);

        long otherUserId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));

        ResourceUpdateRequest update = new ResourceUpdateRequest("해킹 시도", null, 1L, 1);
        mockMvc.perform(put("/api/v1/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer otherToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("리소스 삭제 성공 시 204 반환")
    void deleteResource_success() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);

        mockMvc.perform(delete("/api/v1/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("가용 시간 수정 성공 시 변경된 시간 반환")
    void updateAvailableTime_success() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);
        Long availableTimeId = addAvailableTime(resourceId,
                LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 11, 0));

        AvailableTimeUpdateRequest update = new AvailableTimeUpdateRequest(
                LocalDateTime.of(2026, 7, 1, 14, 0), LocalDateTime.of(2026, 7, 1, 15, 0));

        mockMvc.perform(put("/api/v1/available-times/{id}", availableTimeId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(availableTimeId))
                .andExpect(jsonPath("$.startTime").value("2026-07-01T14:00:00"))
                .andExpect(jsonPath("$.endTime").value("2026-07-01T15:00:00"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("존재하지 않는 가용 시간 수정 시 404 반환")
    void updateAvailableTime_notFound() throws Exception {
        AvailableTimeUpdateRequest update = new AvailableTimeUpdateRequest(
                LocalDateTime.of(2026, 7, 1, 14, 0), LocalDateTime.of(2026, 7, 1, 15, 0));

        mockMvc.perform(put("/api/v1/available-times/{id}", 9999L)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_008"));
    }

    @Test
    @DisplayName("가용 시간 삭제 성공 시 204 반환")
    void deleteAvailableTime_success() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);
        Long availableTimeId = addAvailableTime(resourceId,
                LocalDateTime.of(2026, 7, 3, 10, 0), LocalDateTime.of(2026, 7, 3, 11, 0));

        mockMvc.perform(delete("/api/v1/available-times/{id}", availableTimeId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("다른 유저가 리소스 등록 시 403 반환")
    void registerResource_forbidden() throws Exception {
        long otherUserId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));

        ResourceCreateRequest request = new ResourceCreateRequest("별채 A", "설명", 150000L, 2);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchant.getId())
                        .header("Authorization", "Bearer otherToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("다른 유저가 가용 시간 추가 시 403 반환")
    void addAvailableTime_forbidden() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);

        long otherUserId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));

        AvailableTimeCreateRequest request = new AvailableTimeCreateRequest(
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 11, 0));
        mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .header("Authorization", "Bearer otherToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 리소스 삭제 시 404 반환")
    void deleteResource_notFound() throws Exception {
        mockMvc.perform(delete("/api/v1/resources/{resourceId}", 9999L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_007"));
    }

    @Test
    @DisplayName("다른 유저가 리소스 삭제 시 403 반환")
    void deleteResource_forbidden() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);

        long otherUserId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));

        mockMvc.perform(delete("/api/v1/resources/{resourceId}", resourceId)
                        .header("Authorization", "Bearer otherToken"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 가용 시간 삭제 시 404 반환")
    void deleteAvailableTime_notFound() throws Exception {
        mockMvc.perform(delete("/api/v1/available-times/{id}", 9999L)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RSV_008"));
    }

    @Test
    @DisplayName("다른 유저가 가용 시간 삭제 시 403 반환")
    void deleteAvailableTime_forbidden() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);
        Long availableTimeId = addAvailableTime(resourceId,
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 1, 11, 0));

        long otherUserId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));

        mockMvc.perform(delete("/api/v1/available-times/{id}", availableTimeId)
                        .header("Authorization", "Bearer otherToken"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("다른 유저가 가용 시간 수정 시 403 반환")
    void updateAvailableTime_forbidden() throws Exception {
        Long resourceId = registerResource("별채 A", 150000L);
        Long availableTimeId = addAvailableTime(resourceId,
                LocalDateTime.of(2026, 9, 2, 10, 0), LocalDateTime.of(2026, 9, 2, 11, 0));

        long otherUserId = userIdSeq.incrementAndGet();
        given(jwtVerifier.verify(any())).willReturn(new AuthPrincipal(otherUserId, Role.USER));

        AvailableTimeUpdateRequest update = new AvailableTimeUpdateRequest(
                LocalDateTime.of(2026, 9, 2, 14, 0), LocalDateTime.of(2026, 9, 2, 15, 0));
        mockMvc.perform(put("/api/v1/available-times/{id}", availableTimeId)
                        .header("Authorization", "Bearer otherToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    private Long registerResource(String name, Long price) throws Exception {
        ResourceCreateRequest request = new ResourceCreateRequest(name, "설명", price, 4);
        String response = mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchant.getId())
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long addAvailableTime(Long resourceId, LocalDateTime start, LocalDateTime end) throws Exception {
        AvailableTimeCreateRequest request = new AvailableTimeCreateRequest(start, end);
        String response = mockMvc.perform(post("/api/v1/resources/{resourceId}/available-times", resourceId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}