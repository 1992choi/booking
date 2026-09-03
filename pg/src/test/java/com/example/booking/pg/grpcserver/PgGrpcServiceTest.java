package com.example.booking.pg.grpcserver;

import com.example.booking.pg.grpc.ApproveRequest;
import com.example.booking.pg.grpc.ApproveResponse;
import com.example.booking.pg.grpc.CancelRequest;
import com.example.booking.pg.grpc.CancelResponse;
import com.example.booking.pg.service.PgService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PgGrpcServiceTest {

    PgService pgService;
    PgGrpcService pgGrpcService;

    @BeforeEach
    void setUp() {
        pgService = new PgService();
        pgGrpcService = new PgGrpcService(pgService);
    }

    @Test
    @DisplayName("실패율이 0이면 approve 는 성공 응답을 onNext 로 전달한다")
    void approve_success() {
        ReflectionTestUtils.setField(pgService, "failureRate", 0.0);
        StreamObserver<ApproveResponse> responseObserver = Mockito.mock(StreamObserver.class);

        pgGrpcService.approve(
                ApproveRequest.newBuilder().setTransactionId("TXN-1").setAmount(10000L).build(),
                responseObserver);

        ArgumentCaptor<ApproveResponse> captor = ArgumentCaptor.forClass(ApproveResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(Mockito.any());
        assertThat(captor.getValue().getPgTransactionId()).startsWith("PG-");
    }

    @Test
    @DisplayName("실패율이 1이면 approve 는 FAILED_PRECONDITION 으로 onError 를 호출한다")
    void approve_declined() {
        ReflectionTestUtils.setField(pgService, "failureRate", 1.0);
        StreamObserver<ApproveResponse> responseObserver = Mockito.mock(StreamObserver.class);

        pgGrpcService.approve(
                ApproveRequest.newBuilder().setTransactionId("TXN-1").setAmount(10000L).build(),
                responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        verify(responseObserver, never()).onNext(Mockito.any());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                .isEqualTo(Status.FAILED_PRECONDITION.getCode());
    }

    @Test
    @DisplayName("실패율이 0이면 cancel 은 원 거래 ID를 유지한 성공 응답을 전달한다")
    void cancel_success() {
        ReflectionTestUtils.setField(pgService, "failureRate", 0.0);
        StreamObserver<CancelResponse> responseObserver = Mockito.mock(StreamObserver.class);

        pgGrpcService.cancel(
                CancelRequest.newBuilder().setPgTransactionId("PG-1").build(),
                responseObserver);

        ArgumentCaptor<CancelResponse> captor = ArgumentCaptor.forClass(CancelResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();
        assertThat(captor.getValue().getPgTransactionId()).isEqualTo("PG-1");
    }

}
