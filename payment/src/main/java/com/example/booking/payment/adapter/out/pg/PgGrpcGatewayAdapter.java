package com.example.booking.payment.adapter.out.pg;

import com.example.booking.payment.application.port.out.PaymentDeclinedException;
import com.example.booking.payment.application.port.out.PgClientPort;
import com.example.booking.payment.domain.Payment;
import com.example.booking.pg.grpc.PgServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "booking.pg.protocol", havingValue = "grpc")
@RequiredArgsConstructor
public class PgGrpcGatewayAdapter implements PgClientPort {

    private final PgServiceGrpc.PgServiceBlockingStub pgServiceBlockingStub;

    @Override
    public String charge(Payment payment) {
        try {
            com.example.booking.pg.grpc.ApproveResponse response = pgServiceBlockingStub.approve(
                    com.example.booking.pg.grpc.ApproveRequest.newBuilder()
                            .setTransactionId(payment.getId().toString())
                            .setAmount(payment.getAmount())
                            .build());

            return response.getPgTransactionId();
        } catch (StatusRuntimeException e) {
            throw new PaymentDeclinedException(declineReason(e));
        }
    }

    @Override
    public void cancel(String pgTransactionId) {
        try {
            pgServiceBlockingStub.cancel(
                    com.example.booking.pg.grpc.CancelRequest.newBuilder()
                            .setPgTransactionId(pgTransactionId)
                            .build());
        } catch (StatusRuntimeException e) {
            throw new PaymentDeclinedException(declineReason(e));
        }
    }

    private String declineReason(StatusRuntimeException e) {
        String description = e.getStatus().getDescription();

        return description != null ? description : "PG 서버 오류";
    }

}
