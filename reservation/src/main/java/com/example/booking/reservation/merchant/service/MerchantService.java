package com.example.booking.reservation.merchant.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import com.example.booking.reservation.admin.dto.AdminReservationPageResponse;
import com.example.booking.reservation.admin.dto.AdminReservationResponse;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.dto.PageResponse;
import com.example.booking.reservation.dto.ReservationResponse;
import com.example.booking.reservation.error.ReservationErrorCode;
import com.example.booking.reservation.merchant.domain.DailyMerchantStats;
import com.example.booking.reservation.merchant.domain.DailyMerchantStatsRepository;
import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantRepository;
import com.example.booking.reservation.merchant.domain.MerchantType;
import com.example.booking.reservation.merchant.dto.DailyMerchantStatsResponse;
import com.example.booking.reservation.merchant.dto.MerchantCreateRequest;
import com.example.booking.reservation.merchant.dto.MerchantDetailResponse;
import com.example.booking.reservation.merchant.dto.MerchantSummaryResponse;
import com.example.booking.reservation.merchant.dto.MerchantUpdateRequest;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.domain.ResourceRepository;
import com.example.booking.reservation.service.ReservationService;
import com.example.booking.reservation.user.domain.UserSync;
import com.example.booking.reservation.user.domain.UserSyncRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ResourceRepository resourceRepository;
    private final ReservationService reservationService;
    private final UserSyncRepository userSyncRepository;
    private final DailyMerchantStatsRepository dailyMerchantStatsRepository;
    private final CacheManager cacheManager;

    // L1: 로컬 JVM 메모리 캐시. L2(Redis)보다 TTL을 짧게 둬 다중 인스턴스 간 데이터 불일치 폭을 줄인다.
    private final com.github.benmanes.caffeine.cache.Cache<Long, Merchant> merchantLocalCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    @Transactional
    @CacheEvict(value = "merchants", allEntries = true)
    public Merchant register(Long userId, MerchantCreateRequest request) {
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .userId(userId)
                .name(request.name())
                .phone(request.phone())
                .type(request.type())
                .build());
        log.info("업체 등록 merchantId={}, userId={}", merchant.getId(), userId);

        return merchant;
    }

    @Transactional(readOnly = true)
    public List<Merchant> getMyMerchants(Long userId) {
        return merchantRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Merchant getById(Long merchantId) {
        Merchant local = merchantLocalCache.getIfPresent(merchantId);
        if (local != null) {
            return local;
        }

        Cache redisCache = cacheManager.getCache("merchant");
        Cache.ValueWrapper wrapper = redisCache.get(merchantId);
        Merchant merchant = wrapper != null
                ? (Merchant) wrapper.get()
                : merchantRepository.findById(merchantId)
                        .orElseThrow(() -> new BusinessException(ReservationErrorCode.MERCHANT_NOT_FOUND));

        redisCache.put(merchantId, merchant);
        merchantLocalCache.put(merchantId, merchant);

        return merchant;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "merchant", key = "#merchantId"),
            @CacheEvict(value = "merchants", allEntries = true)
    })
    public Merchant update(Long userId, Long merchantId, MerchantUpdateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.MERCHANT_NOT_FOUND));
        if (!merchant.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        merchant.update(request.name(), request.phone(), request.type());
        merchantLocalCache.invalidate(merchantId);
        log.info("업체 수정 merchantId={}, userId={}", merchantId, userId);

        return merchant;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "merchants", key = "{#type, #pageable}")
    public PageResponse<MerchantSummaryResponse> getAll(MerchantType type, Pageable pageable) {
        Page<Merchant> merchants = type == null
                ? merchantRepository.findAll(pageable)
                : merchantRepository.findAllByType(type, pageable);

        return PageResponse.from(merchants.map(MerchantSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public MerchantDetailResponse getDetail(Long merchantId) {
        Merchant merchant = getById(merchantId);
        return MerchantDetailResponse.from(merchant, resourceRepository.findAllByMerchantId(merchantId));
    }

    @Transactional(readOnly = true)
    public AdminReservationPageResponse getMerchantReservations(Long userId, Long merchantId, ReservationStatus status, int page, int size) {
        Merchant merchant = getById(merchantId);
        if (!merchant.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        List<Long> resourceIds = resourceRepository.findAllByMerchantId(merchantId).stream()
                .map(Resource::getId)
                .toList();
        if (resourceIds.isEmpty()) {
            return new AdminReservationPageResponse(List.of(), page, size, 0L, 0);
        }

        PageResponse<ReservationResponse> pageResponse = reservationService.getByResourceIds(
                resourceIds, status, PageRequest.of(page, size));

        List<Long> userIds = pageResponse.content().stream()
                .map(ReservationResponse::userId).distinct().toList();
        Map<Long, String> userNames = userIds.isEmpty() ? Map.of() :
                userSyncRepository.findAllByIdIn(userIds).stream()
                        .collect(Collectors.toMap(UserSync::getId, UserSync::getName));

        List<AdminReservationResponse> content = pageResponse.content().stream()
                .map(r -> new AdminReservationResponse(
                        r.id(), r.status().name(), r.resourceName(), r.startTime(), r.endTime(),
                        r.headCount(), r.amount(), r.userId(), userNames.get(r.userId())))
                .toList();

        return new AdminReservationPageResponse(content, pageResponse.page(), pageResponse.size(),
                pageResponse.totalElements(), pageResponse.totalPages());
    }

    @Transactional(readOnly = true)
    public List<DailyMerchantStatsResponse> getDailyStats(Long userId, Long merchantId, int year, int month) {
        Merchant merchant = getById(merchantId);
        if (!merchant.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.plusMonths(1).minusDays(1);

        return dailyMerchantStatsRepository
                .findAllByMerchantIdAndStatDateBetweenOrderByStatDateAsc(merchantId, from, to)
                .stream()
                .map(DailyMerchantStatsResponse::from)
                .toList();
    }

}
