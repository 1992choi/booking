package com.example.booking.pg.error;

import com.example.booking.pg.dto.PgErrorResponse;
import com.example.booking.pg.service.TransactionDeclinedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PgExceptionHandler {

    @ExceptionHandler(TransactionDeclinedException.class)
    public ResponseEntity<PgErrorResponse> handleDeclined(TransactionDeclinedException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new PgErrorResponse("TRANSACTION_DECLINED", e.getMessage()));
    }

}
