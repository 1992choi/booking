package com.example.booking.api.owner.service;

import com.example.booking.api.error.ApiErrorCode;
import com.example.booking.api.owner.domain.Owner;
import com.example.booking.api.owner.domain.OwnerRepository;
import com.example.booking.api.owner.dto.OwnerCreateRequest;
import com.example.booking.api.owner.dto.OwnerUpdateRequest;
import java.util.List;
import com.example.booking.api.resource.domain.ResourceRepository;
import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;

    @Transactional
    public Owner register(Long userId, OwnerCreateRequest request) {
        if (ownerRepository.existsByUserId(userId)) {
            throw new BusinessException(ApiErrorCode.OWNER_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        user.promoteToOwner();

        return ownerRepository.save(Owner.builder()
                .userId(userId)
                .name(request.name())
                .phone(request.phone())
                .type(request.type())
                .build());
    }

    @Transactional(readOnly = true)
    public Owner getByUserId(Long userId) {
        return ownerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.OWNER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Owner getById(Long ownerId) {
        return ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.OWNER_NOT_FOUND));
    }

    @Transactional
    public Owner update(Long userId, OwnerUpdateRequest request) {
        Owner owner = ownerRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.OWNER_NOT_FOUND));
        owner.update(request.name(), request.phone(), request.type());
        return owner;
    }

    @Transactional(readOnly = true)
    public List<Owner> getAll() {
        return ownerRepository.findAll();
    }
}
