package com.example.booking.payment.adapter.out.pg;

import com.example.booking.payment.adapter.out.pg.dto.PgApproveRequest;
import com.example.booking.payment.adapter.out.pg.dto.PgApproveResponse;
import com.example.booking.payment.adapter.out.pg.dto.PgCancelRequest;
import com.example.booking.payment.adapter.out.pg.dto.PgErrorResponse;
import com.example.booking.payment.application.port.out.PaymentDeclinedException;
import com.example.booking.payment.application.port.out.PgClientPort;
import com.example.booking.payment.domain.Payment;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "booking.pg.protocol", havingValue = "rest", matchIfMissing = true)
@RequiredArgsConstructor
public class PgGatewayAdapter implements PgClientPort {

    private final RestClient pgRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public String charge(Payment payment) {
        try {
            PgApproveResponse response = pgRestClient.post()
                    .uri("/pg/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PgApproveRequest(payment.getId().toString(), payment.getAmount()))
                    .retrieve()
                    .body(PgApproveResponse.class);

            return response.pgTransactionId();
        } catch (RestClientResponseException e) {
            throw new PaymentDeclinedException(parseDeclineReason(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new PaymentDeclinedException("PG 서버 오류");
        }
    }

    @Override
    public void cancel(String pgTransactionId) {
        try {
            pgRestClient.post()
                    .uri("/pg/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PgCancelRequest(pgTransactionId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new PaymentDeclinedException(parseDeclineReason(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new PaymentDeclinedException("PG 서버 오류");
        }
    }

    private String parseDeclineReason(String body) {
        try {
            return objectMapper.readValue(body, PgErrorResponse.class).message();
        } catch (JacksonException e) {
            return "결제가 거절되었습니다.";
        }
    }

}
