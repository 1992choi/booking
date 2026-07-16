package com.example.booking.payment.adapter.out.persistence;

import com.example.booking.payment.application.port.out.LoadPaymentPort;
import com.example.booking.payment.application.port.out.SavePaymentPort;
import com.example.booking.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements LoadPaymentPort, SavePaymentPort {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Optional<Payment> loadByReservationId(Long reservationId) {
        return paymentJpaRepository.findByReservationId(reservationId)
                .map(PaymentMapper::toDomain);
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = payment.getId() == null
                ? PaymentMapper.toNewJpaEntity(payment)
                : paymentJpaRepository.findById(payment.getId())
                        .map(existing -> PaymentMapper.updateJpaEntity(existing, payment))
                        .orElseThrow(() -> new IllegalStateException("Payment not found: " + payment.getId()));

        return PaymentMapper.toDomain(paymentJpaRepository.save(entity));
    }

}
