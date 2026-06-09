package com.example.booking.reservation.resource.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import com.example.booking.reservation.error.ReservationErrorCode;
import com.example.booking.reservation.merchant.domain.MerchantRepository;
import com.example.booking.reservation.resource.domain.AvailableTime;
import com.example.booking.reservation.resource.domain.AvailableTimeRepository;
import com.example.booking.reservation.resource.domain.AvailableTimeStatus;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.domain.ResourceRepository;
import com.example.booking.reservation.resource.dto.AvailableTimeCreateRequest;
import com.example.booking.reservation.resource.dto.AvailableTimeUpdateRequest;
import com.example.booking.reservation.resource.dto.ResourceCreateRequest;
import com.example.booking.reservation.resource.dto.ResourceUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final AvailableTimeRepository availableTimeRepository;
    private final MerchantRepository merchantRepository;

    @Transactional
    public Resource register(Long userId, Long merchantId, ResourceCreateRequest request) {
        if (!merchantRepository.existsById(merchantId)) {
            throw new BusinessException(ReservationErrorCode.MERCHANT_NOT_FOUND);
        }
        validateMerchantAccess(userId, merchantId);

        Resource resource = resourceRepository.save(Resource.builder()
                .merchantId(merchantId)
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .maxCapacity(request.maxCapacity())
                .build());
        log.info("리소스 등록 resourceId={}, merchantId={}, userId={}", resource.getId(), merchantId, userId);

        return resource;
    }

    @Transactional
    public AvailableTime addAvailableTime(Long userId, Long resourceId, AvailableTimeCreateRequest request) {
        Resource resource = getById(resourceId);
        validateMerchantAccess(userId, resource.getMerchantId());

        AvailableTime availableTime = availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resourceId)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(AvailableTimeStatus.OPEN)
                .build());
        log.info("가능 시간 추가 availableTimeId={}, resourceId={}", availableTime.getId(), resourceId);

        return availableTime;
    }

    @Transactional(readOnly = true)
    public List<AvailableTime> getAvailableTimes(Long resourceId, LocalDate date) {
        if (!resourceRepository.existsById(resourceId)) {
            throw new BusinessException(ReservationErrorCode.RESOURCE_NOT_FOUND);
        }

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        return availableTimeRepository.findAllByResourceIdAndStartTimeBetween(resourceId, from, to);
    }

    @Transactional
    public AvailableTime updateAvailableTime(Long userId, Long availableTimeId, AvailableTimeUpdateRequest request) {
        AvailableTime availableTime = availableTimeRepository.findById(availableTimeId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.AVAILABLE_TIME_NOT_FOUND));
        Resource resource = getById(availableTime.getResourceId());
        validateMerchantAccess(userId, resource.getMerchantId());

        availableTime.update(request.startTime(), request.endTime());

        return availableTime;
    }

    @Transactional
    public void deleteAvailableTime(Long userId, Long availableTimeId) {
        AvailableTime availableTime = availableTimeRepository.findById(availableTimeId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.AVAILABLE_TIME_NOT_FOUND));
        Resource resource = getById(availableTime.getResourceId());
        validateMerchantAccess(userId, resource.getMerchantId());

        availableTimeRepository.delete(availableTime);
        log.info("가능 시간 삭제 availableTimeId={}, resourceId={}, userId={}", availableTimeId, resource.getId(), userId);
    }

    @Transactional
    public Resource update(Long userId, Long resourceId, ResourceUpdateRequest request) {
        Resource resource = getById(resourceId);
        validateMerchantAccess(userId, resource.getMerchantId());

        resource.update(request.name(), request.description(), request.price(), request.maxCapacity());
        log.info("리소스 수정 resourceId={}, userId={}", resourceId, userId);

        return resource;
    }

    @Transactional
    public void delete(Long userId, Long resourceId) {
        Resource resource = getById(resourceId);
        validateMerchantAccess(userId, resource.getMerchantId());

        resourceRepository.delete(resource);
        log.info("리소스 삭제 resourceId={}, userId={}", resourceId, userId);
    }

    @Transactional(readOnly = true)
    public Resource getById(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateMerchantAccess(Long userId, Long merchantId) {
        boolean hasMerchantAccess = merchantRepository.findAllByUserId(userId).stream()
                .anyMatch(m -> m.getId().equals(merchantId));
        if (!hasMerchantAccess) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

}
