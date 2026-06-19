package com.example.booking.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job expirePendingReservationsJob;

    @Scheduled(cron = "${booking.batch.expire-cron}")
    public void runExpirePendingReservationsJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLocalDateTime("runAt", LocalDateTime.now())
                .toJobParameters();

        jobLauncher.run(expirePendingReservationsJob, params);
    }

}
