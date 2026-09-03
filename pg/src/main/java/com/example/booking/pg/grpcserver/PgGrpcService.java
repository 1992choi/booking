package com.example.booking.pg.grpcserver;

import com.example.booking.pg.dto.ApproveResponse;
import com.example.booking.pg.dto.CancelResponse;
import com.example.booking.pg.grpc.PgServiceGrpc;
import com.example.booking.pg.service.PgService;
import com.example.booking.pg.service.TransactionDeclinedException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class PgGrpcService extends PgServiceGrpc.PgServiceImplBase {

    private final PgService pgService;

    @Override
    public void approve(com.example.booking.pg.grpc.ApproveRequest request,
            StreamObserver<com.example.booking.pg.grpc.ApproveResponse> responseObserver) {

        try {
            ApproveResponse response = pgService.approve(
                    new com.example.booking.pg.dto.ApproveRequest(request.getTransactionId(), request.getAmount()));

            responseObserver.onNext(com.example.booking.pg.grpc.ApproveResponse.newBuilder()
                    .setPgTransactionId(response.pgTransactionId())
                    .setApprovedAt(response.approvedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .build());
            responseObserver.onCompleted();
        } catch (TransactionDeclinedException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void cancel(com.example.booking.pg.grpc.CancelRequest request,
            StreamObserver<com.example.booking.pg.grpc.CancelResponse> responseObserver) {

        try {
            CancelResponse response = pgService.cancel(
                    new com.example.booking.pg.dto.CancelRequest(request.getPgTransactionId()));

            responseObserver.onNext(com.example.booking.pg.grpc.CancelResponse.newBuilder()
                    .setPgTransactionId(response.pgTransactionId())
                    .setCancelledAt(response.cancelledAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .build());
            responseObserver.onCompleted();
        } catch (TransactionDeclinedException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        }
    }

}
