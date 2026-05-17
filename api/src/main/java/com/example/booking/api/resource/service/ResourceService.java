package com.example.booking.api.resource.service;

import com.example.booking.api.error.ApiErrorCode;
import com.example.booking.api.merchant.domain.MerchantRepository;
import com.example.booking.api.resource.domain.AvailableTime;
import com.example.booking.api.resource.domain.AvailableTimeRepository;
import com.example.booking.api.resource.domain.AvailableTimeStatus;
import com.example.booking.api.resource.domain.Resource;
import com.example.booking.api.resource.domain.ResourceRepository;
import com.example.booking.api.resource.dto.AvailableTimeCreateRequest;
import com.example.booking.api.resource.dto.ResourceCreateRequest;
import com.example.booking.api.resource.dto.ResourceUpdateRequest;
import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final AvailableTimeRepository availableTimeRepository;
    private final MerchantRepository merchantRepository;

    @Transactional
    public Resource register(Long merchantId, ResourceCreateRequest request) {
        if (!merchantRepository.existsById(merchantId)) {
            throw new BusinessException(ApiErrorCode.MERCHANT_NOT_FOUND);
        }
        return resourceRepository.save(Resource.builder()
                .merchantId(merchantId)
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .maxCapacity(request.maxCapacity())
                .build());
    }

    @Transactional
    public AvailableTime addAvailableTime(Long resourceId, AvailableTimeCreateRequest request) {
        if (!resourceRepository.existsById(resourceId)) {
            throw new BusinessException(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        return availableTimeRepository.save(AvailableTime.builder()
                .resourceId(resourceId)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(AvailableTimeStatus.OPEN)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AvailableTime> getAvailableTimes(Long resourceId, LocalDate date) {
        if (!resourceRepository.existsById(resourceId)) {
            throw new BusinessException(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return availableTimeRepository.findAllByResourceIdAndStartTimeBetween(resourceId, from, to);
    }

    @Transactional
    public Resource update(Long userId, Long resourceId, ResourceUpdateRequest request) {
        Resource resource = getById(resourceId);
        validateMerchantAccess(userId, resource.getMerchantId());
        resource.update(request.name(), request.description(), request.price(), request.maxCapacity());
        return resource;
    }

    @Transactional
    public void delete(Long userId, Long resourceId) {
        Resource resource = getById(resourceId);
        validateMerchantAccess(userId, resource.getMerchantId());
        resourceRepository.delete(resource);
    }

    @Transactional(readOnly = true)
    public Resource getById(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateMerchantAccess(Long userId, Long merchantId) {
        boolean hasMerchantAccess = merchantRepository.findAllByUserId(userId).stream()
                .anyMatch(m -> m.getId().equals(merchantId));
        if (!hasMerchantAccess) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
