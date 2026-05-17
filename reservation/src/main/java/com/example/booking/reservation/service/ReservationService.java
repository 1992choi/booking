package com.example.booking.reservation.service;

import com.example.booking.core.error.BusinessException;
import com.example.booking.reservation.client.AvailableTimeSnapshot;
import com.example.booking.reservation.client.ResourceClient;
import com.example.booking.reservation.client.ResourceSnapshot;
import com.example.booking.reservation.domain.Reservation;
import com.example.booking.reservation.domain.ReservationRepository;
import com.example.booking.reservation.domain.ReservationStatus;
import com.example.booking.reservation.dto.CalendarReservationResponse;
import com.example.booking.reservation.dto.CreateReservationRequest;
import com.example.booking.reservation.dto.PageResponse;
import com.example.booking.reservation.dto.ReservationResponse;
import com.example.booking.reservation.error.ReservationErrorCode;
import com.example.booking.reservation.event.ReservationCancelledDomainEvent;
import com.example.booking.reservation.event.ReservationCreatedDomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceClient resourceClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<ReservationResponse> create(Long userId, CreateReservationRequest request) {
        ResourceSnapshot resource = resourceClient.fetch(request.resourceId());

        if (request.headCount() > resource.maxCapacity()) {
            throw new BusinessException(ReservationErrorCode.CAPACITY_EXCEEDED);
        }

        List<AvailableTimeSnapshot> slots = resourceClient.fetchAvailableTimes(request.availableTimeIds());

        for (AvailableTimeSnapshot slot : slots) {
            if (!slot.resourceId().equals(request.resourceId())) {
                throw new BusinessException(ReservationErrorCode.CONFLICT);
            }
            if (!"OPEN".equals(slot.status())) {
                throw new BusinessException(ReservationErrorCode.CONFLICT);
            }
            boolean hasOverlap = !reservationRepository.findOverlapping(
                    request.resourceId(), slot.startTime(), slot.endTime()).isEmpty();
            if (hasOverlap) {
                throw new BusinessException(ReservationErrorCode.CONFLICT);
            }
        }

        List<Reservation> reservations = slots.stream()
                .map(slot -> Reservation.builder()
                        .availableTimeId(slot.id())
                        .userId(userId)
                        .resourceId(request.resourceId())
                        .resourceName(resource.name())
                        .startTime(slot.startTime())
                        .endTime(slot.endTime())
                        .status(ReservationStatus.PENDING)
                        .headCount(request.headCount())
                        .amount(resource.price())
                        .build())
                .toList();

        reservationRepository.saveAll(reservations);

        reservations.forEach(reservation ->
                eventPublisher.publishEvent(new ReservationCreatedDomainEvent(
                        reservation.getId(), userId, request.resourceId(), resource.price(),
                        reservation.getAvailableTimeId())));

        return reservations.stream().map(ReservationResponse::from).toList();
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
        eventPublisher.publishEvent(new ReservationCancelledDomainEvent(
                reservationId, userId, reservation.getAvailableTimeId()));
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getAll(LocalDate date, ReservationStatus status, Pageable pageable) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        return PageResponse.from(
                (status != null
                        ? reservationRepository.findByDateRangeAndStatus(from, to, status, pageable)
                        : reservationRepository.findByDateRange(from, to, pageable)
                ).map(ReservationResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, List<CalendarReservationResponse>> getCalendar(int year, int month) {
        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to = from.plusMonths(1);

        return reservationRepository.findByMonthRange(from, to).stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStartTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.mapping(CalendarReservationResponse::from, Collectors.toList())
                ));
    }

    @Transactional
    public ReservationResponse confirm(Long reservationId) {
        Reservation reservation = findOrThrow(reservationId);
        reservation.confirm();
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse adminCancel(Long reservationId) {
        Reservation reservation = findOrThrow(reservationId);
        reservation.cancel();
        eventPublisher.publishEvent(new ReservationCancelledDomainEvent(
                reservationId, reservation.getUserId(), reservation.getAvailableTimeId()));
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public void cancelByPaymentFailure(Long reservationId) {
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            reservation.cancel();
            eventPublisher.publishEvent(new ReservationCancelledDomainEvent(
                    reservationId, reservation.getUserId(), reservation.getAvailableTimeId()));
        });
    }

    private Reservation findOrThrow(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));
    }
}