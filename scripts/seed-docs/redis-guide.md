# Redis Development Guide

## Data Structures

### Strings
Basic key-value storage:
- SET/GET for simple values
- SETEX for values with expiration
- INCR/DECR for counters

### Hashes
Object-like storage:
- HSET/HGET for individual fields
- HGETALL for entire hash
- HINCRBY for numeric field increments

### Lists
Ordered collections:
- LPUSH/RPUSH for adding
- LPOP/RPOP for removing
- LRANGE for reading ranges

### Sets
Unordered unique collections:
- SADD for adding members
- SMEMBERS for all members
- SINTER for intersections

### Sorted Sets
Ordered by score:
- ZADD with scores
- ZRANGE for ordered retrieval
- ZINCRBY for score updates

## Common Patterns

### Caching
Check cache first, fallback to database:
1. Get from Redis
2. If miss, fetch from database
3. Store in Redis with TTL
4. Return result

### Rate Limiting
Sliding window implementation:
- INCR key on each request
- EXPIRE key with window duration
- Compare count to limit

### Distributed Locking
Use SETNX for atomic lock acquisition:
- Set lock with TTL to prevent deadlocks
- Verify ownership before releasing
- Use unique value per lock holder

## Best Practices

### Key Naming
- Use colons as separators: user:123:profile
- Include version for cache invalidation
- Keep keys short but readable

### Memory Management
- Set maxmemory and eviction policy
- Use appropriate TTLs
- Monitor memory usage
- Consider Redis Cluster for large datasets

### Connection Management
- Use connection pooling
- Configure appropriate timeouts
- Handle connection failures gracefully

## Monitoring
Key commands:
- INFO memory
- INFO stats
- SLOWLOG GET 10
- CLIENT LIST
