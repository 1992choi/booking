package com.example.booking.batch.job;

import com.example.booking.batch.tasklet.DailyMerchantStatsTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class DailyMerchantStatsJobConfig {

    private final DailyMerchantStatsTasklet tasklet;

    @Bean
    public Job dailyMerchantStatsJob(JobRepository jobRepository, Step dailyMerchantStatsStep) {
        return new JobBuilder("dailyMerchantStatsJob", jobRepository)
                .start(dailyMerchantStatsStep)
                .build();
    }

    @Bean
    public Step dailyMerchantStatsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("dailyMerchantStatsStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

}
