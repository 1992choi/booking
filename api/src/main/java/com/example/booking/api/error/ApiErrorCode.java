package com.example.booking.api.error;

import com.example.booking.core.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ApiErrorCode implements ErrorCode {

    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "API_001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "API_002", "이메일 또는 비밀번호가 일치하지 않습니다."),
    OWNER_ALREADY_EXISTS(HttpStatus.CONFLICT, "API_003", "이미 등록된 업체가 있습니다."),
    OWNER_NOT_FOUND(HttpStatus.NOT_FOUND, "API_004", "업체를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}