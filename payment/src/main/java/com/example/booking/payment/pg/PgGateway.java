package com.example.booking.payment.pg;

import com.example.booking.payment.domain.Payment;
import com.example.booking.payment.pg.dto.PgApproveRequest;
import com.example.booking.payment.pg.dto.PgApproveResponse;
import com.example.booking.payment.pg.dto.PgCancelRequest;
import com.example.booking.payment.pg.dto.PgErrorResponse;
import com.example.booking.payment.service.PaymentDeclinedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class PgGateway {

    private final RestClient pgRestClient;
    private final ObjectMapper objectMapper;

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