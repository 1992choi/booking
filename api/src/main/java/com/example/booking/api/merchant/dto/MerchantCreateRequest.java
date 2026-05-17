package com.example.booking.api.merchant.dto;

import com.example.booking.api.merchant.domain.MerchantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MerchantCreateRequest(

        @NotBlank(message = "업체명은 필수입니다.")
        String name,

        @NotBlank(message = "전화번호는 필수입니다.")
        String phone,

        @NotNull(message = "업체 타입은 필수입니다.")
        MerchantType type
) {}
