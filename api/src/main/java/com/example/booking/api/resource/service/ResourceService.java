package com.example.booking.api.resource.service;

import com.example.booking.api.error.ApiErrorCode;
import com.example.booking.api.owner.domain.OwnerRepository;
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
    private final OwnerRepository ownerRepository;

    @Transactional
    public Resource register(Long ownerId, ResourceCreateRequest request) {
        if (!ownerRepository.existsById(ownerId)) {
            throw new BusinessException(ApiErrorCode.OWNER_NOT_FOUND);
        }
        return resourceRepository.save(Resource.builder()
                .ownerId(ownerId)
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
        validateOwnership(userId, resource.getOwnerId());
        resource.update(request.name(), request.description(), request.price(), request.maxCapacity());
        return resource;
    }

    @Transactional
    public void delete(Long userId, Long resourceId) {
        Resource resource = getById(resourceId);
        validateOwnership(userId, resource.getOwnerId());
        resourceRepository.delete(resource);
    }

    @Transactional(readOnly = true)
    public Resource getById(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateOwnership(Long userId, Long ownerId) {
        ownerRepository.findByUserId(userId)
                .filter(owner -> owner.getId().equals(ownerId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
    }
}