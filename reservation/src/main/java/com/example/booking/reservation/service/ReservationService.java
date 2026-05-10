package com.example.booking.reservation.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.reservation.client.ResourceClient;
import com.example.booking.reservation.client.ResourceSnapshot;
import com.example.booking.reservation.domain.Reservation;
import com.example.booking.reservation.domain.ReservationRepository;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.dto.CreateReservationRequest;
import com.example.booking.reservation.dto.PageResponse;
import com.example.booking.reservation.dto.ReservationResponse;
import com.example.booking.reservation.error.ReservationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceClient resourceClient;

    @Transactional
    public ReservationResponse create(Long userId, CreateReservationRequest request) {
        ResourceSnapshot resource = resourceClient.fetch(request.resourceId());

        if (request.headCount() > resource.maxCapacity()) {
            throw new BusinessException(ReservationErrorCode.CAPACITY_EXCEEDED);
        }

        boolean hasOverlap = !reservationRepository.findOverlapping(
                request.resourceId(), request.startTime(), request.endTime()).isEmpty();
        if (hasOverlap) {
            throw new BusinessException(ReservationErrorCode.CONFLICT);
        }

        Reservation reservation = Reservation.builder()
                .userId(userId)
                .resourceId(request.resourceId())
                .resourceName(resource.name())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(ReservationStatus.PENDING)
                .headCount(request.headCount())
                .amount(resource.price())
                .build();

        reservationRepository.save(reservation);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getById(Long reservationId) {
        Reservation reservation = findOrThrow(reservationId);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getMyReservations(Long userId, ReservationStatus status, Pageable pageable) {
        return PageResponse.from(
                reservationRepository.findByUserIdAndStatus(userId, status, pageable)
                        .map(ReservationResponse::from)
        );
    }

    @Transactional
    public ReservationResponse cancel(Long userId, Long reservationId) {
        Reservation reservation = findOrThrow(reservationId);

        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException(ReservationErrorCode.NOT_OWNER);
        }

        reservation.cancel();
        return ReservationResponse.from(reservation);
    }

    private Reservation findOrThrow(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));
    }
}