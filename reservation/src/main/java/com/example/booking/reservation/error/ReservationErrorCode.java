package com.example.booking.reservation.error;

import com.example.booking.core.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {

    CONFLICT(HttpStatus.CONFLICT, "RSV_001", "이미 예약된 시간대입니다."),
    LOCK_FAILED(HttpStatus.CONFLICT, "RSV_002", "잠시 후 다시 시도해주세요."),
    CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "RSV_003", "최대 수용 인원을 초과했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "RSV_004", "예약을 찾을 수 없습니다."),
    NOT_MY_RESERVATION(HttpStatus.FORBIDDEN, "RSV_005", "본인 예약만 취소할 수 있습니다."),
    MERCHANT_NOT_FOUND(HttpStatus.NOT_FOUND, "RSV_006", "업체를 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RSV_007", "예약 대상을 찾을 수 없습니다."),
    AVAILABLE_TIME_NOT_FOUND(HttpStatus.NOT_FOUND, "RSV_008", "이용 가능 시간을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public HttpStatus status() { return status; }

    @Override
    public String code() { return code; }

    @Override
    public String message() { return message; }
}