package com.example.booking.batch.job;

import com.example.booking.batch.tasklet.ExpirePendingReservationsTasklet;
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
public class ExpirePendingReservationsJobConfig {

    private final ExpirePendingReservationsTasklet tasklet;

    @Bean
    public Job expirePendingReservationsJob(JobRepository jobRepository, Step expirePendingReservationsStep) {
        return new JobBuilder("expirePendingReservationsJob", jobRepository)
                .start(expirePendingReservationsStep)
                .build();
    }

    @Bean
    public Step expirePendingReservationsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("expirePendingReservationsStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

}
