# Microservices Architecture Patterns

## Service Design

### Single Responsibility
Each service should:
- Own its domain data
- Have clear boundaries
- Be independently deployable
- Scale independently

### Service Communication

#### Synchronous (REST/gRPC)
- Use for real-time operations
- Implement circuit breakers
- Set appropriate timeouts
- Handle partial failures

#### Asynchronous (Messaging)
- Use for eventual consistency
- Kafka for event streaming
- RabbitMQ for task queues
- Implement idempotent consumers

## Resilience Patterns

### Circuit Breaker
Prevent cascade failures by wrapping external calls with circuit breaker annotations. Configure fallback methods for graceful degradation.

### Retry with Backoff
Implement retry logic with exponential backoff for transient failures. Use annotations like @Retry with fallback methods.

### Bulkhead
Isolate failures:
- Separate thread pools per service
- Limit concurrent requests
- Fail fast when pool exhausted

## Data Patterns

### Database per Service
- Each service owns its database
- No direct database access between services
- Use events for data synchronization

### Saga Pattern
For distributed transactions:
1. Choreography: Events trigger next steps
2. Orchestration: Central coordinator manages flow

### CQRS
Separate read and write models:
- Command side: Handle writes
- Query side: Optimized for reads
- Sync via events

## Observability

### Logging
- Structured JSON logging
- Include correlation IDs
- Log at service boundaries

### Metrics
- Request rate, error rate, duration
- Business metrics
- Resource utilization

### Tracing
- Distributed tracing with Zipkin/Jaeger
- Propagate trace context
- Sample appropriately for production
