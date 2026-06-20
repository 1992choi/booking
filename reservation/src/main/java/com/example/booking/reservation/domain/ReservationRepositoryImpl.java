package com.example.booking.reservation.domain;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public class ReservationRepositoryImpl implements ReservationRepositoryCustom {

    private static final QReservation r = QReservation.reservation;

    private final JPAQueryFactory queryFactory;

    public ReservationRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<Reservation> findOverlapping(Long resourceId, LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .selectFrom(r)
                .where(
                        r.resourceId.eq(resourceId),
                        r.status.ne(ReservationStatus.CANCELLED),
                        r.startTime.lt(end),
                        r.endTime.gt(start)
                )
                .fetch();
    }

    @Override
    public List<Reservation> findByMonthRange(List<Long> resourceIds, LocalDateTime from, LocalDateTime to) {
        return queryFactory
                .selectFrom(r)
                .where(
                        r.resourceId.in(resourceIds),
                        r.startTime.goe(from),
                        r.startTime.lt(to)
                )
                .fetch();
    }

    @Override
    public int sumHeadCountByAvailableTimeId(Long availableTimeId) {
        Integer result = queryFactory
                .select(r.headCount.sum())
                .from(r)
                .where(
                        r.availableTimeId.eq(availableTimeId),
                        r.status.ne(ReservationStatus.CANCELLED)
                )
                .fetchOne();

        return result != null ? result : 0;
    }

    @Override
    public Page<Reservation> findByUser(Long userId, ReservationStatus status, Pageable pageable) {
        BooleanExpression condition = r.userId.eq(userId);
        if (status != null) {
            condition = condition.and(r.status.eq(status));
        }

        List<Reservation> content = queryFactory
                .selectFrom(r)
                .where(condition)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(r.count())
                .from(r)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Reservation> findByResources(List<Long> resourceIds, ReservationStatus status, Pageable pageable) {
        BooleanExpression condition = r.resourceId.in(resourceIds);
        if (status != null) {
            condition = condition.and(r.status.eq(status));
        }

        List<Reservation> content = queryFactory
                .selectFrom(r)
                .where(condition)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(r.count())
                .from(r)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

}
