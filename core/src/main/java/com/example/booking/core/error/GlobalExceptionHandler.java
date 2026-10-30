package com.example.booking.core.error;

import com.example.booking.core.auth.AuthPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException e,
                                                         @AuthenticationPrincipal AuthPrincipal principal) {
        ErrorCode ec = e.getErrorCode();

        if (principal != null) {
            MDC.put("userId", String.valueOf(principal.userId()));
        }
        try {
            log.warn("Business exception: code={}, status={}, message={}", ec.code(), ec.status(), ec.message());
        } finally {
            MDC.remove("userId");
        }

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ec.status(), ec.message());
        pd.setProperty("code", ec.code());

        return ResponseEntity.status(ec.status()).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setProperty("code", CommonErrorCode.BAD_REQUEST.code());

        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        pd.setProperty("code", CommonErrorCode.BAD_REQUEST.code());

        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                CommonErrorCode.INTERNAL_ERROR.message()
        );
        pd.setProperty("code", CommonErrorCode.INTERNAL_ERROR.code());

        return ResponseEntity.internalServerError().body(pd);
    }

}