package com.example.booking.reservation.scheduler;

import com.example.booking.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    @Value("${booking.reminder.window-minutes}")
    private int windowMinutes;

    private final ReservationService reservationService;

    @Scheduled(cron = "${booking.reminder.cron}")
    public void run() {
        reservationService.sendReminders(windowMinutes);
    }

}
