package com.example.booking.payment.adapter.out.pg;

import com.example.booking.payment.application.port.out.PaymentDeclinedException;
import com.example.booking.payment.domain.Payment;
import com.example.booking.payment.domain.PaymentStatus;
import com.example.booking.pg.grpc.ApproveRequest;
import com.example.booking.pg.grpc.ApproveResponse;
import com.example.booking.pg.grpc.CancelRequest;
import com.example.booking.pg.grpc.PgServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

class PgGrpcGatewayAdapterTest {

    PgServiceGrpc.PgServiceBlockingStub stub = Mockito.mock(PgServiceGrpc.PgServiceBlockingStub.class);
    PgGrpcGatewayAdapter adapter = new PgGrpcGatewayAdapter(stub);

    @Test
    @DisplayName("charge 성공 시 pgTransactionId 반환")
    void charge_success() {
        Payment payment = Payment.reconstruct(1L, 10L, 100L, 15000L, PaymentStatus.PENDING, null, null, null);
        given(stub.approve(any(ApproveRequest.class))).willReturn(
                ApproveResponse.newBuilder().setPgTransactionId("PG-1").setApprovedAt("2026-01-01T00:00:00").build());

        String pgTransactionId = adapter.charge(payment);

        assertThat(pgTransactionId).isEqualTo("PG-1");
    }

    @Test
    @DisplayName("charge 실패 시 gRPC 에러 설명을 담아 PaymentDeclinedException 발생")
    void charge_declined_throwsPaymentDeclinedException() {
        Payment payment = Payment.reconstruct(1L, 10L, 100L, 15000L, PaymentStatus.PENDING, null, null, null);
        given(stub.approve(any(ApproveRequest.class))).willThrow(
                Status.FAILED_PRECONDITION.withDescription("잔액 부족").asRuntimeException());

        assertThatThrownBy(() -> adapter.charge(payment))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("잔액 부족");
    }

    @Test
    @DisplayName("cancel 실패 시 gRPC 에러 설명을 담아 PaymentDeclinedException 발생")
    void cancel_declined_throwsPaymentDeclinedException() {
        given(stub.cancel(any(CancelRequest.class))).willThrow(
                Status.FAILED_PRECONDITION.withDescription("이미 취소된 거래").asRuntimeException());

        assertThatThrownBy(() -> adapter.cancel("PG-1"))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("이미 취소된 거래");
    }

    @Test
    @DisplayName("설명 없는 gRPC 에러는 기본 메시지로 대체")
    void charge_declined_withoutDescription_usesDefaultMessage() {
        Payment payment = Payment.reconstruct(1L, 10L, 100L, 15000L, PaymentStatus.PENDING, null, null, null);
        given(stub.approve(any(ApproveRequest.class))).willThrow(
                new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> adapter.charge(payment))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("PG 서버 오류");
    }

}
