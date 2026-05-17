package com.example.booking.api.merchant.service;

import com.example.booking.api.error.ApiErrorCode;
import com.example.booking.api.merchant.domain.Merchant;
import com.example.booking.api.merchant.domain.MerchantRepository;
import com.example.booking.api.merchant.dto.MerchantCreateRequest;
import com.example.booking.api.merchant.dto.MerchantUpdateRequest;
import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;

    @Transactional
    public Merchant register(Long userId, MerchantCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        user.promoteToMerchant();

        return merchantRepository.save(Merchant.builder()
                .userId(userId)
                .name(request.name())
                .phone(request.phone())
                .type(request.type())
                .build());
    }

    @Transactional(readOnly = true)
    public List<Merchant> getMyMerchants(Long userId) {
        return merchantRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Merchant getById(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.MERCHANT_NOT_FOUND));
    }

    @Transactional
    public Merchant update(Long userId, Long merchantId, MerchantUpdateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.MERCHANT_NOT_FOUND));
        if (!merchant.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        merchant.update(request.name(), request.phone(), request.type());
        return merchant;
    }

    @Transactional(readOnly = true)
    public List<Merchant> getAll() {
        return merchantRepository.findAll();
    }
}
