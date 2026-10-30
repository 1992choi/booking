package com.example.booking.api.assistant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationClient {

    private final RestClient reservationRestClient;

    public String getMyReservations(String jwt) {
        try {
            return reservationRestClient.get()
                    .uri("/api/v1/reservations/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("예약 조회 실패 — reason={}", e.getMessage());

            return "예약 조회에 실패했습니다.";
        }
    }

}
