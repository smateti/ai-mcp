package com.enterprise.cobol.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CobolAnalysisBatchConfig {

    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());
        launcher.afterPropertiesSet();
        return launcher;
    }

    @Bean
    public Job cobolAnalysisJob(JobRepository jobRepository,
                                 Step parseStep,
                                 Step llmAnalysisStep,
                                 Step vectorizeStep) {
        return new JobBuilder("cobolAnalysisJob", jobRepository)
                .start(parseStep)
                .next(llmAnalysisStep)
                .next(vectorizeStep)
                .build();
    }

    @Bean
    public Step parseStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          CobolParseTasklet tasklet) {
        return new StepBuilder("parseStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Step llmAnalysisStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 LlmAnalysisTasklet tasklet) {
        return new StepBuilder("llmAnalysisStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Step vectorizeStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               VectorizeTasklet tasklet) {
        return new StepBuilder("vectorizeStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
