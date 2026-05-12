package com.example.booking.api.admin.client;

import com.example.booking.api.admin.dto.AdminCalendarEntry;
import com.example.booking.api.admin.dto.AdminReservationPageResponse;
import com.example.booking.api.admin.dto.AdminReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReservationClient {

    private final RestClient reservationRestClient;

    public AdminReservationPageResponse getAll(LocalDate date, String status, int page, int size, String token) {
        return reservationRestClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/v1/internal/reservations")
                            .queryParam("date", date)
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (status != null) builder.queryParam("status", status);
                    return builder.build();
                })
                .header("Authorization", token)
                .retrieve()
                .body(AdminReservationPageResponse.class);
    }

    public Map<LocalDate, List<AdminCalendarEntry>> getCalendar(int year, int month, String token) {
        return reservationRestClient.get()
                .uri("/api/v1/internal/reservations/calendar?year={year}&month={month}", year, month)
                .header("Authorization", token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public AdminReservationResponse confirm(Long reservationId, String token) {
        return reservationRestClient.put()
                .uri("/api/v1/internal/reservations/{id}/confirm", reservationId)
                .header("Authorization", token)
                .retrieve()
                .body(AdminReservationResponse.class);
    }

    public AdminReservationResponse cancel(Long reservationId, String token) {
        return reservationRestClient.put()
                .uri("/api/v1/internal/reservations/{id}/cancel", reservationId)
                .header("Authorization", token)
                .retrieve()
                .body(AdminReservationResponse.class);
    }
}