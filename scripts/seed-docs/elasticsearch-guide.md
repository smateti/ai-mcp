# Elasticsearch Development Guide

## Index Design

### Mapping Best Practices
Define field types explicitly:
- text: Full-text search with analyzer
- keyword: Exact matches, aggregations
- date: Timestamps
- scaled_float: Decimal numbers with scaling factor

Use multi-fields for text+keyword combination.

### Index Settings
Configure:
- number_of_shards: 3 for most cases
- number_of_replicas: 1 for redundancy
- refresh_interval: 30s for bulk, 1s for real-time
- Custom analyzers for specific needs

## Query Patterns

### Full-Text Search
Use multi_match for searching across fields:
- Boost important fields with ^2
- Use fuzziness for typo tolerance
- Choose appropriate type (best_fields, most_fields)

### Filtering and Aggregation
Combine queries with bool:
- must: Required matches (affects score)
- filter: Required matches (no score, cached)
- should: Optional matches
- must_not: Exclusions

Aggregations: terms, avg, sum, date_histogram

## Bulk Operations

### Bulk Indexing
Use _bulk API for efficiency:
- Batch size: 1000-5000 documents
- Disable refresh during bulk
- Use routing for related documents

## Performance Tuning

### Query Optimization
- Use filters for exact matches (cached)
- Avoid deep pagination (use search_after)
- Limit returned fields with _source
- Use doc values for sorting/aggregations

### Index Optimization
- Use index templates
- Implement index lifecycle management (ILM)
- Roll over time-based indices
- Force merge read-only indices

## Monitoring
Key endpoints:
- _cluster/health
- _cat/indices
- _cat/shards
- _nodes/stats
