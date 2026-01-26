# Spring Batch Development Guide

## Introduction to Spring Batch
Spring Batch is a lightweight framework for developing robust batch applications. It provides reusable functions for processing large volumes of records including logging, transaction management, job processing statistics, and resource management.

## Core Concepts

### Job
A Job is an entity that encapsulates an entire batch process. It is composed of one or more Steps.

### Step
A Step represents an independent, sequential phase of a batch job. Each Step has exactly one ItemReader, ItemProcessor, and ItemWriter.

### ItemReader
Reads data from a source (database, file, message queue).

### ItemProcessor
Transforms or validates the data.

### ItemWriter
Writes data to a destination.

## Basic Job Configuration
```java
@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Bean
    public Job importUserJob(JobRepository jobRepository, Step step1) {
        return new JobBuilder("importUserJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .flow(step1)
            .end()
            .build();
    }

    @Bean
    public Step step1(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      ItemReader<UserInput> reader,
                      ItemProcessor<UserInput, User> processor,
                      ItemWriter<User> writer) {
        return new StepBuilder("step1", jobRepository)
            .<UserInput, User>chunk(100, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(10)
            .skip(DataFormatException.class)
            .build();
    }
}
```

## Reading Data

### FlatFileItemReader for CSV files
```java
@Bean
public FlatFileItemReader<UserInput> csvReader() {
    return new FlatFileItemReaderBuilder<UserInput>()
        .name("userCsvReader")
        .resource(new ClassPathResource("users.csv"))
        .delimited()
        .names("firstName", "lastName", "email")
        .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
            setTargetType(UserInput.class);
        }})
        .linesToSkip(1) // Skip header
        .build();
}
```

### JdbcCursorItemReader for Database
```java
@Bean
public JdbcCursorItemReader<User> databaseReader(DataSource dataSource) {
    return new JdbcCursorItemReaderBuilder<User>()
        .name("userDbReader")
        .dataSource(dataSource)
        .sql("SELECT id, name, email FROM users WHERE processed = false")
        .rowMapper(new BeanPropertyRowMapper<>(User.class))
        .build();
}
```

## Processing Data
```java
@Component
public class UserProcessor implements ItemProcessor<UserInput, User> {

    @Override
    public User process(UserInput input) throws Exception {
        // Skip invalid records by returning null
        if (input.getEmail() == null || !input.getEmail().contains("@")) {
            return null;
        }

        return User.builder()
            .firstName(input.getFirstName().trim().toUpperCase())
            .lastName(input.getLastName().trim().toUpperCase())
            .email(input.getEmail().toLowerCase())
            .createdAt(LocalDateTime.now())
            .build();
    }
}
```

## Writing Data

### JdbcBatchItemWriter
```java
@Bean
public JdbcBatchItemWriter<User> databaseWriter(DataSource dataSource) {
    return new JdbcBatchItemWriterBuilder<User>()
        .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
        .sql("INSERT INTO users (first_name, last_name, email, created_at) " +
             "VALUES (:firstName, :lastName, :email, :createdAt)")
        .dataSource(dataSource)
        .build();
}
```

### FlatFileItemWriter for CSV output
```java
@Bean
public FlatFileItemWriter<User> csvWriter() {
    return new FlatFileItemWriterBuilder<User>()
        .name("userCsvWriter")
        .resource(new FileSystemResource("output/users.csv"))
        .delimited()
        .names("firstName", "lastName", "email")
        .headerCallback(writer -> writer.write("First Name,Last Name,Email"))
        .build();
}
```

## Job Scheduling
```java
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job importUserJob;

    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    public void runJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addLong("time", System.currentTimeMillis())
            .addString("inputFile", "users_" + LocalDate.now() + ".csv")
            .toJobParameters();

        jobLauncher.run(importUserJob, params);
    }
}
```

## Error Handling and Skip Logic
```java
@Bean
public Step processStep(JobRepository jobRepository,
                        PlatformTransactionManager txManager) {
    return new StepBuilder("processStep", jobRepository)
        .<Input, Output>chunk(100, txManager)
        .reader(reader())
        .processor(processor())
        .writer(writer())
        .faultTolerant()
        .skipLimit(100)
        .skip(ParseException.class)
        .skip(ValidationException.class)
        .retryLimit(3)
        .retry(DeadlockLoserDataAccessException.class)
        .listener(new SkipListener<>() {
            @Override
            public void onSkipInRead(Throwable t) {
                log.warn("Skipped record during read: {}", t.getMessage());
            }
        })
        .build();
}
```

## Partitioning for Parallel Processing
```java
@Bean
public Step masterStep(JobRepository jobRepository,
                       Step workerStep,
                       Partitioner partitioner) {
    return new StepBuilder("masterStep", jobRepository)
        .partitioner("workerStep", partitioner)
        .step(workerStep)
        .gridSize(4) // Number of parallel threads
        .taskExecutor(new SimpleAsyncTaskExecutor())
        .build();
}

@Bean
public Partitioner rangePartitioner(DataSource dataSource) {
    return gridSize -> {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        int min = jdbcTemplate.queryForObject("SELECT MIN(id) FROM users", Integer.class);
        int max = jdbcTemplate.queryForObject("SELECT MAX(id) FROM users", Integer.class);
        int targetSize = (max - min) / gridSize + 1;

        for (int i = 0; i < gridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            context.putInt("minId", min + (targetSize * i));
            context.putInt("maxId", min + (targetSize * (i + 1)) - 1);
            partitions.put("partition" + i, context);
        }
        return partitions;
    };
}
```

## Job Monitoring
```java
@Component
public class JobCompletionListener implements JobExecutionListener {

    @Override
    public void afterJob(JobExecution execution) {
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            log.info("Job completed successfully!");
            log.info("Read count: {}", execution.getStepExecutions()
                .stream()
                .mapToLong(StepExecution::getReadCount)
                .sum());
        } else if (execution.getStatus() == BatchStatus.FAILED) {
            log.error("Job failed with exceptions: {}",
                execution.getAllFailureExceptions());
        }
    }
}
```

## Best Practices
1. Use chunk-oriented processing for large datasets
2. Implement idempotent jobs (can be restarted safely)
3. Use database-backed JobRepository for production
4. Configure appropriate chunk sizes (typically 100-1000)
5. Implement proper skip and retry logic
6. Monitor job execution with listeners
7. Use partitioning for parallel processing
