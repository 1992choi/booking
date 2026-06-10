package com.example.booking.reservation.service;

import com.example.booking.core.error.BusinessException;
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
import com.example.booking.reservation.admin.dto.AdminReservationResponse;
import com.example.booking.reservation.resource.domain.AvailableTime;
import com.example.booking.reservation.resource.domain.AvailableTimeRepository;
import com.example.booking.reservation.resource.domain.AvailableTimeStatus;
import com.example.booking.reservation.resource.domain.Resource;
import com.example.booking.reservation.resource.domain.ResourceRepository;
import com.example.booking.reservation.user.domain.UserSync;
import com.example.booking.reservation.user.domain.UserSyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final AvailableTimeRepository availableTimeRepository;
    private final UserSyncRepository userSyncRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<ReservationResponse> create(Long userId, CreateReservationRequest request) {
        Resource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.RESOURCE_NOT_FOUND));

        if (request.headCount() > resource.getMaxCapacity()) {
            throw new BusinessException(ReservationErrorCode.CAPACITY_EXCEEDED);
        }

        List<AvailableTime> slots = availableTimeRepository.findAllById(request.availableTimeIds());
        validateSlots(slots, request.resourceId(), request.headCount(), resource.getMaxCapacity());

        List<Reservation> reservations = buildReservations(slots, userId, resource, request.headCount());
        reservationRepository.saveAll(reservations);

        reservations.forEach(reservation ->
                eventPublisher.publishEvent(new ReservationCreatedDomainEvent(
                        reservation.getId(), userId, request.resourceId(), resource.getPrice(),
                        reservation.getAvailableTimeId())));
        log.info("예약 생성 userId={}, resourceId={}, slotCount={}", userId, request.resourceId(), reservations.size());

        return reservations.stream().map(ReservationResponse::from).toList();
    }

    private void validateSlots(List<AvailableTime> slots, Long resourceId, int headCount, int maxCapacity) {
        for (AvailableTime slot : slots) {
            if (!slot.getResourceId().equals(resourceId)) {
                throw new BusinessException(ReservationErrorCode.CONFLICT);
            }
            if (slot.getStatus() == AvailableTimeStatus.BLOCKED) {
                throw new BusinessException(ReservationErrorCode.CONFLICT);
            }
            int occupied = reservationRepository.findOverlapping(resourceId, slot.getStartTime(), slot.getEndTime())
                    .stream().mapToInt(Reservation::getHeadCount).sum();
            if (occupied + headCount > maxCapacity) {
                throw new BusinessException(ReservationErrorCode.CONFLICT);
            }
        }
    }

    private List<Reservation> buildReservations(List<AvailableTime> slots, Long userId, Resource resource, int headCount) {
        return slots.stream()
                .map(slot -> Reservation.builder()
                        .availableTimeId(slot.getId())
                        .userId(userId)
                        .resourceId(resource.getId())
                        .resourceName(resource.getName())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .status(ReservationStatus.PENDING)
                        .headCount(headCount)
                        .amount(resource.getPrice())
                        .build())
                .toList();
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
            throw new BusinessException(ReservationErrorCode.NOT_MY_RESERVATION);
        }

        reservation.cancel();
        eventPublisher.publishEvent(new ReservationCancelledDomainEvent(
                reservationId, userId, reservation.getAvailableTimeId()));
        log.info("예약 취소 reservationId={}, userId={}", reservationId, userId);

        return ReservationResponse.from(reservation);
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
    public AdminReservationResponse confirm(Long reservationId) {
        Reservation reservation = findOrThrow(reservationId);
        reservation.confirm();
        return toAdminResponse(ReservationResponse.from(reservation));
    }

    @Transactional
    public AdminReservationResponse adminCancel(Long reservationId) {
        Reservation reservation = findOrThrow(reservationId);
        reservation.cancel();
        eventPublisher.publishEvent(new ReservationCancelledDomainEvent(
                reservationId, reservation.getUserId(), reservation.getAvailableTimeId()));
        return toAdminResponse(ReservationResponse.from(reservation));
    }

    private AdminReservationResponse toAdminResponse(ReservationResponse r) {
        String userName = userSyncRepository.findById(r.userId())
                .map(UserSync::getName).orElse(null);
        return new AdminReservationResponse(
                r.id(), r.status().name(), r.resourceName(),
                r.startTime(), r.endTime(), r.headCount(), r.amount(), r.userId(), userName);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getByResourceIds(List<Long> resourceIds, ReservationStatus status, Pageable pageable) {
        return PageResponse.from(
                (status != null
                        ? reservationRepository.findByResourceIdInAndStatus(resourceIds, status, pageable)
                        : reservationRepository.findByResourceIdIn(resourceIds, pageable)
                ).map(ReservationResponse::from)
        );
    }

    @Transactional
    public void cancelByPaymentFailure(Long reservationId) {
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            reservation.cancel();
            eventPublisher.publishEvent(new ReservationCancelledDomainEvent(
                    reservationId, reservation.getUserId(), reservation.getAvailableTimeId()));
            log.warn("결제 실패로 예약 취소 reservationId={}, userId={}", reservationId, reservation.getUserId());
        });
    }

    private Reservation findOrThrow(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ReservationErrorCode.NOT_FOUND));
    }

}
