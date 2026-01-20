# PostgreSQL Development Guide

## Connection Management

### Connection String
Format: postgresql://username:password@host:5432/database?sslmode=require

### Connection Pooling
- Use HikariCP for connection pooling
- Set maximum pool size based on: connections = (core_count * 2) + effective_spindle_count
- Configure idle timeout (10 minutes recommended)
- Set connection timeout (30 seconds recommended)

## Query Optimization

### Index Design
- B-tree index for equality and range queries
- Partial index for filtered queries
- Composite index (column order matters)
- GIN index for full-text search

### Query Analysis
Use EXPLAIN ANALYZE to understand query plans. Check pg_stat_user_indexes for index usage statistics.

## Data Types

### Recommended Types
- IDs: UUID or BIGSERIAL
- Text: VARCHAR(n) or TEXT
- Dates: TIMESTAMPTZ (always with timezone)
- Money: NUMERIC(19,4)
- JSON: JSONB (not JSON)

### JSONB Operations
- Query with ->> operator
- Index JSONB fields
- Update with || operator

## Transactions

### Isolation Levels
- READ COMMITTED (default): Good for most cases
- REPEATABLE READ: For consistent reports
- SERIALIZABLE: For critical financial operations

### Best Practices
- Keep transactions short
- Avoid user interaction during transactions
- Use savepoints for partial rollback
- Handle deadlocks with retry logic

## Maintenance

### Regular Tasks
- ANALYZE: Update statistics
- VACUUM: Reclaim space
- REINDEX: Rebuild indexes

### Monitoring Queries
Check pg_stat_activity for long-running queries. Monitor pg_stat_user_tables for table bloat.
