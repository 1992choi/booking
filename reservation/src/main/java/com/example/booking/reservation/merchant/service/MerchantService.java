package com.example.booking.reservation.merchant.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import com.example.booking.reservation.admin.dto.AdminReservationPageResponse;
import com.example.booking.reservation.admin.dto.AdminReservationResponse;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.dto.PageResponse;
import com.example.booking.reservation.dto.ReservationResponse;
import com.example.booking.reservation.error.ReservationErrorCode;
import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantRepository;
import com.example.booking.reservation.merchant.dto.MerchantCreateRequest;
import com.example.booking.reservation.merchant.dto.MerchantDetailResponse;
import com.example.booking.reservation.merchant.dto.MerchantUpdateRequest;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.domain.ResourceRepository;
import com.example.booking.reservation.service.ReservationService;
import com.example.booking.reservation.user.domain.UserSync;
import com.example.booking.reservation.user.domain.UserSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    @CacheEvict(value = "merchants", key = "'all'")
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
    @Cacheable(value = "merchant", key = "#merchantId")
    public Merchant getById(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.MERCHANT_NOT_FOUND));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "merchant", key = "#merchantId"),
            @CacheEvict(value = "merchants", key = "'all'")
    })
    public Merchant update(Long userId, Long merchantId, MerchantUpdateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.MERCHANT_NOT_FOUND));
        if (!merchant.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        merchant.update(request.name(), request.phone(), request.type());
        log.info("업체 수정 merchantId={}, userId={}", merchantId, userId);

        return merchant;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "merchants", key = "'all'")
    public List<Merchant> getAll() {
        return merchantRepository.findAll();
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

}
