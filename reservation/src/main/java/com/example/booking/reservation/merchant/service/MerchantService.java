package com.example.booking.reservation.merchant.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import com.example.booking.reservation.error.ReservationErrorCode;
import com.example.booking.reservation.merchant.domain.Merchant;
import com.example.booking.reservation.merchant.domain.MerchantRepository;
import com.example.booking.reservation.merchant.dto.MerchantCreateRequest;
import com.example.booking.reservation.merchant.dto.MerchantUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;

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

}
