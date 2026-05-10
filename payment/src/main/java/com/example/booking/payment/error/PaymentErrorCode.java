package com.example.booking.payment.error;

import com.example.booking.core.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "PAY_001", "결제 처리에 실패했습니다."),
    REFUND_NOT_ALLOWED(HttpStatus.CONFLICT, "PAY_002", "환불 가능 상태가 아닙니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "PAY_003", "결제 내역을 찾을 수 없습니다.");

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