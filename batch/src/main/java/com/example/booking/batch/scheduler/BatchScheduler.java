package com.example.booking.batch.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job expirePendingReservationsJob;
    private final Job dailyMerchantStatsJob;

    public BatchScheduler(
            JobLauncher jobLauncher,
            @Qualifier("expirePendingReservationsJob") Job expirePendingReservationsJob,
            @Qualifier("dailyMerchantStatsJob") Job dailyMerchantStatsJob) {
        this.jobLauncher = jobLauncher;
        this.expirePendingReservationsJob = expirePendingReservationsJob;
        this.dailyMerchantStatsJob = dailyMerchantStatsJob;
    }

    @Scheduled(cron = "${booking.batch.expire-cron}")
    public void runExpirePendingReservationsJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLocalDateTime("runAt", LocalDateTime.now())
                .toJobParameters();

        jobLauncher.run(expirePendingReservationsJob, params);
    }

    @Scheduled(cron = "${booking.batch.stats-cron}")
    public void runDailyMerchantStatsJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLocalDateTime("runAt", LocalDateTime.now())
                .toJobParameters();

        jobLauncher.run(dailyMerchantStatsJob, params);
    }

}
