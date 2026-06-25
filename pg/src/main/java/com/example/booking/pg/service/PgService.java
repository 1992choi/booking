package com.example.booking.pg.service;

import com.example.booking.pg.dto.ApproveRequest;
import com.example.booking.pg.dto.ApproveResponse;
import com.example.booking.pg.dto.CancelRequest;
import com.example.booking.pg.dto.CancelResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PgService {

    private static final List<String> APPROVE_DECLINE_REASONS = List.of(
            "잔액 부족", "카드 한도 초과", "카드 정지", "일시적 오류"
    );

    private static final List<String> CANCEL_DECLINE_REASONS = List.of(
            "취소 불가 거래", "이미 취소된 거래", "일시적 오류"
    );

    @Value("${pg.failure-rate}")
    private double failureRate;

    public ApproveResponse approve(ApproveRequest request) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new TransactionDeclinedException(randomFrom(APPROVE_DECLINE_REASONS));
        }

        return new ApproveResponse("PG-" + UUID.randomUUID(), LocalDateTime.now());
    }

    public CancelResponse cancel(CancelRequest request) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new TransactionDeclinedException(randomFrom(CANCEL_DECLINE_REASONS));
        }

        return new CancelResponse(request.pgTransactionId(), LocalDateTime.now());
    }

    private <T> T randomFrom(List<T> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

}
