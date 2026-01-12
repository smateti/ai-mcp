# Question Frequency Tracking - Smart Caching

## Overview

The system now tracks **which questions are frequently asked** and only caches those that meet a threshold. This prevents wasting cache space on one-time questions.

## How It Works

### The Threshold System

```
Question asked for 1st time:
  ↓
Track in database (askCount = 1)
  ↓
Call LLM (NOT cached)
  ↓
Store in database

Question asked for 2nd time:
  ↓
Update database (askCount = 2)
  ↓
Call LLM (still not cached, but meets threshold now)
  ↓
Mark as "cached" in database

Question asked for 3rd+ time:
  ↓
Check database (askCount >= 2, isCached = true)
  ↓
Use EhCache (FAST!)
  ↓
Return cached response
```

### Key Features

1. **Frequency Threshold**: Questions must be asked **2+ times** to be cached
2. **Question Normalization**: "What is AI?" and "what is ai?" are treated as the same
3. **Statistics API**: View which questions are most frequently asked
4. **Database Tracking**: All questions tracked in H2 database

## Database Schema

### question_frequency Table

| Column        | Type      | Description                           |
|---------------|-----------|---------------------------------------|
| id            | BIGINT    | Primary key                           |
| question_hash | VARCHAR   | SHA-256 hash of normalized question   |
| question      | TEXT      | Normalized question text              |
| ask_count     | INTEGER   | How many times question was asked     |
| first_asked   | TIMESTAMP | When first asked                      |
| last_asked    | TIMESTAMP | Most recent time asked                |
| is_cached     | BOOLEAN   | Whether this question is cached       |

## API Endpoints

### GET /api/stats/frequency

View question frequency statistics.

**Response:**
```json
{
  "uniqueQuestions": 2,
  "totalAsks": 6,
  "cachedQuestions": 2,
  "cacheThreshold": 2,
  "top10FrequentQuestions": [
    {
      "question": "what is ai",
      "askCount": 3,
      "isCached": true
    },
    {
      "question": "hello world",
      "askCount": 3,
      "isCached": true
    }
  ]
}
```

**Fields:**
- `uniqueQuestions`: Total number of different questions asked
- `totalAsks`: Total number of times any question was asked
- `cachedQuestions`: Number of questions that qualify for caching (askCount >= 2)
- `cacheThreshold`: Minimum times a question must be asked to be cached (currently: 2)
- `top10FrequentQuestions`: Top 10 most frequently asked questions with stats

## Question Normalization

To handle variations, questions are normalized before comparison:

| Original Question      | Normalized Question |
|-----------------------|---------------------|
| "What is AI?"         | "what is ai"        |
| "What  is   AI??"     | "what is ai"        |
| "WHAT IS AI"          | "what is ai"        |
| "What is AI."         | "what is ai"        |

**Normalization rules:**
1. Convert to lowercase
2. Remove punctuation (?,!.,;:)
3. Collapse multiple spaces to single space
4. Trim leading/trailing spaces

## Testing Results

### Test 1: Question Frequency Tracking

```bash
# First time asking "What is AI?"
curl -X POST .../messages -d '{"message":"What is AI?"}'
# Result: askCount = 1, isCached = false, NOT cached

# Second time asking "What is AI?"
curl -X POST .../messages -d '{"message":"What is AI?"}'
# Result: askCount = 2, isCached = true, MEETS THRESHOLD

# Third time asking "What is AI?"
curl -X POST .../messages -d '{"message":"What is AI?"}'
# Result: Uses cached response (fast!)
```

### Test 2: Statistics API

```bash
curl http://localhost:8080/api/stats/frequency
```

```json
{
  "uniqueQuestions": 2,
  "totalAsks": 6,
  "cachedQuestions": 2,
  "cacheThreshold": 2,
  "top10FrequentQuestions": [
    {"question": "what is ai", "askCount": 3, "isCached": true},
    {"question": "hello world", "askCount": 3, "isCached": true}
  ]
}
```

## Benefits

### 1. Efficient Cache Usage
- **Before**: Every question cached (wastes space)
- **After**: Only frequently asked questions cached

### 2. Visibility
- See which questions are most common
- Identify patterns in user queries
- Optimize documentation based on frequent questions

### 3. Cost Optimization
- Don't waste cache on one-time questions
- Focus caching on high-value repeated queries

### 4. Better Analytics
- Track question trends over time
- Identify popular topics
- Improve content strategy

## Use Cases

### Customer Support
```
Top questions:
1. "How do I reset my password?" (45 times)
2. "What are your business hours?" (32 times)
3. "How do I cancel my subscription?" (28 times)

Action: Cache these common questions for instant responses
```

### Documentation
```
Most accessed docs:
1. "How to install?" (120 times)
2. "Getting started guide" (98 times)
3. "API authentication" (76 times)

Action: Improve these docs, add to FAQ
```

### Educational Platform
```
Common student questions:
1. "What is polymorphism?" (67 times)
2. "Explain recursion" (54 times)
3. "Difference between == and ===" (43 times)

Action: Create dedicated learning modules
```

## Configuration

### Cache Threshold

Currently set to **2** in `QuestionFrequencyService.java`:

```java
private static final int CACHE_THRESHOLD = 2;
```

To change the threshold:
1. Modify the constant
2. Rebuild the application
3. Restart

**Recommendations:**
- **High traffic sites**: Threshold = 5-10
- **Medium traffic**: Threshold = 2-3
- **Low traffic**: Threshold = 2
- **Development**: Threshold = 1 (cache everything for testing)

## Architecture

```
User asks question
      ↓
QuestionFrequencyService
      ↓
  [Check database]
      ↓
  ┌─────────┬─────────┐
  │         │         │
First    Second     Third+
Time     Time       Time
  │         │         │
askCount  askCount  askCount
= 1       = 2       >= 2
  │         │         │
NOT       MEETS     CACHED!
cached    threshold   ↓
  │         │       EhCache
  ↓         ↓         ↓
Call LLM  Call LLM  Return cached
  ↓         ↓         ↓
Save to DB Save to DB Save to DB
  ↓         ↓         ↓
Return    Return    Return
```

## Files Created

### Entities
- `QuestionFrequency.java` - Database entity for tracking

### Repositories
- `QuestionFrequencyRepository.java` - Data access layer

### Services
- `QuestionFrequencyService.java` - Frequency tracking logic
- Updated `CachedChatService.java` - Integrated frequency checking

### Controllers
- `FrequencyStatsController.java` - Statistics API

## Database Queries

### View all questions
```sql
SELECT * FROM question_frequency ORDER BY ask_count DESC;
```

### View cached questions only
```sql
SELECT question, ask_count, last_asked
FROM question_frequency
WHERE is_cached = true
ORDER BY ask_count DESC;
```

### View one-time questions
```sql
SELECT question, first_asked
FROM question_frequency
WHERE ask_count = 1
ORDER BY first_asked DESC;
```

### Get cache efficiency
```sql
SELECT
  COUNT(*) as total_questions,
  SUM(CASE WHEN is_cached THEN 1 ELSE 0 END) as cached_questions,
  ROUND(100.0 * SUM(CASE WHEN is_cached THEN 1 ELSE 0 END) / COUNT(*), 2) as cache_rate
FROM question_frequency;
```

## Future Enhancements

### 1. Time-based Decay
- Reduce askCount over time for old questions
- Focus on recently frequent questions

### 2. Adaptive Threshold
- Automatically adjust threshold based on traffic
- Lower threshold during low traffic, higher during peaks

### 3. Category-based Caching
- Different thresholds for different question types
- Technical questions: threshold = 2
- General questions: threshold = 5

### 4. Smart Suggestions
- "Did you mean...?" for similar questions
- Suggest popular questions to users

### 5. A/B Testing
- Test different thresholds
- Measure impact on response time and cache hit rate

## Monitoring

### Key Metrics to Track

1. **Cache Hit Rate**: `cachedQuestions / uniqueQuestions`
2. **Average Ask Count**: `totalAsks / uniqueQuestions`
3. **Top Questions**: Most frequently asked
4. **Cache Efficiency**: Questions cached vs total questions

### Health Check

```bash
# Get current stats
curl http://localhost:8080/api/stats/frequency

# Calculate cache efficiency
cache_rate = (cachedQuestions / uniqueQuestions) * 100

# Good: > 20% (many repeat questions)
# Medium: 10-20% (some repeats)
# Low: < 10% (mostly unique questions)
```

## Summary

✅ **Smart frequency-based caching implemented**
✅ **Questions tracked in database**
✅ **Threshold system prevents cache waste**
✅ **Statistics API for monitoring**
✅ **Question normalization for better matching**
✅ **Top 10 frequent questions visible**

The system now intelligently caches only frequently asked questions, optimizing cache usage and providing valuable insights into user behavior!
