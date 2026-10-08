package com.example.booking.reservation.merchant.service;

import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantRepository;
import com.example.booking.reservation.merchant.domain.MerchantType;
import com.example.booking.reservation.merchant.dto.MerchantUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MerchantServiceTest {

    @Autowired
    MerchantService merchantService;

    @Autowired
    MerchantRepository merchantRepository;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = merchantRepository.save(Merchant.builder()
                .userId(1L)
                .name("캐시 테스트 업체")
                .phone("02-0000-0000")
                .type(MerchantType.CLASS)
                .build());
    }

    @Test
    @DisplayName("한 번 조회된 업체는 로컬(L1) 캐시에서 서빙되어 DB에서 삭제돼도 조회된다")
    void getById_servesFromLocalCacheAfterFirstLookup() {
        merchantService.getById(merchant.getId());

        merchantRepository.deleteById(merchant.getId());

        Merchant cached = merchantService.getById(merchant.getId());

        assertThat(cached.getId()).isEqualTo(merchant.getId());
    }

    @Test
    @DisplayName("로컬(L1) 캐시가 비어도 Redis(L2)에 있으면 DB 조회 없이 서빙된다")
    void getById_fallsBackToRedisWhenLocalCacheMiss() {
        merchantService.getById(merchant.getId());

        Object localCache = ReflectionTestUtils.getField(merchantService, "merchantLocalCache");
        ((com.github.benmanes.caffeine.cache.Cache<?, ?>) localCache).invalidateAll();
        merchantRepository.deleteById(merchant.getId());

        Merchant fromRedis = merchantService.getById(merchant.getId());

        assertThat(fromRedis.getId()).isEqualTo(merchant.getId());
    }

    @Test
    @DisplayName("업체 수정 시 로컬 캐시가 무효화되어 다음 조회에 변경된 값을 반환한다")
    void update_invalidatesLocalCache() {
        merchantService.getById(merchant.getId());

        merchantService.update(merchant.getUserId(), merchant.getId(),
                new MerchantUpdateRequest("변경된 이름", "02-9999-9999", MerchantType.PENSION));

        Merchant updated = merchantService.getById(merchant.getId());

        assertThat(updated.getName()).isEqualTo("변경된 이름");
    }

}
