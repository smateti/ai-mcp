# Spring Batch Development Guide

## Introduction
Spring Batch is our standard framework for batch processing. This guide covers setup, configuration, and common patterns.

## Project Setup

### Dependencies
Add to pom.xml:
- spring-boot-starter-batch
- spring-batch-integration

### Configuration
```yaml
spring:
  batch:
    job:
      enabled: false  # Disable auto-run
    jdbc:
      initialize-schema: always
```

## Job Structure

### Basic Job Definition
A job consists of one or more steps. Each step has:
- ItemReader: Reads data from source
- ItemProcessor: Transforms data
- ItemWriter: Writes data to destination

### Step Types
1. **Chunk-oriented**: Process data in chunks (most common)
2. **Tasklet**: Execute single task (cleanup, validation)
3. **Partitioned**: Parallel processing of data partitions

## Common Patterns

### File Processing
- Use FlatFileItemReader for CSV/fixed-width files
- Configure line tokenizer for field parsing
- Implement FieldSetMapper for object mapping

### Database Processing
- Use JdbcPagingItemReader for large tables
- Configure page size (1000-5000 recommended)
- Use JdbcBatchItemWriter for bulk inserts

### Error Handling
- Configure skip policy for recoverable errors
- Use retry template for transient failures
- Implement custom exception handler for logging

## Testing

### Unit Testing
- Use JobLauncherTestUtils for job testing
- Mock external dependencies
- Verify step execution status and counts

### Integration Testing
- Use in-memory database (H2)
- Test full job execution
- Verify data transformations
