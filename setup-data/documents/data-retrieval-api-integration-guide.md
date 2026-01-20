# Data Retrieval and API Integration Guide

## REST API Best Practices

### HTTP Client with WebClient
```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .baseUrl("https://api.example.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(logRequest())
            .filter(logResponse())
            .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }
}
```

### Resilient API Calls with Resilience4j
```java
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final WebClient webClient;

    @CircuitBreaker(name = "externalApi", fallbackMethod = "fallbackGetData")
    @Retry(name = "externalApi")
    @RateLimiter(name = "externalApi")
    public Mono<ApiResponse> getData(String id) {
        return webClient.get()
            .uri("/data/{id}", id)
            .retrieve()
            .bodyToMono(ApiResponse.class);
    }

    private Mono<ApiResponse> fallbackGetData(String id, Throwable t) {
        log.warn("Fallback for getData: {}", t.getMessage());
        return Mono.just(ApiResponse.empty());
    }
}
```

### Resilience4j Configuration
```yaml
resilience4j:
  circuitbreaker:
    instances:
      externalApi:
        registerHealthIndicator: true
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10000
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      externalApi:
        maxAttempts: 3
        waitDuration: 1000
        exponentialBackoffMultiplier: 2
  ratelimiter:
    instances:
      externalApi:
        limitRefreshPeriod: 1s
        limitForPeriod: 10
        timeoutDuration: 500ms
```

## Common Public APIs

### Weather API Integration
```java
@Service
public class WeatherService {

    private final WebClient weatherClient;

    public WeatherService(@Value("${weather.api.key}") String apiKey) {
        this.weatherClient = WebClient.builder()
            .baseUrl("https://api.openweathermap.org/data/2.5")
            .defaultUriVariables(Map.of("appid", apiKey))
            .build();
    }

    public Mono<WeatherData> getCurrentWeather(String city) {
        return weatherClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/weather")
                .queryParam("q", city)
                .queryParam("appid", "{appid}")
                .queryParam("units", "metric")
                .build())
            .retrieve()
            .bodyToMono(WeatherData.class);
    }
}
```

### GitHub API Integration
```java
@Service
public class GitHubService {

    private final WebClient githubClient;

    public GitHubService(@Value("${github.token}") String token) {
        this.githubClient = WebClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Authorization", "Bearer " + token)
            .defaultHeader("Accept", "application/vnd.github.v3+json")
            .build();
    }

    public Mono<Repository> getRepository(String owner, String repo) {
        return githubClient.get()
            .uri("/repos/{owner}/{repo}", owner, repo)
            .retrieve()
            .bodyToMono(Repository.class);
    }

    public Flux<Issue> getIssues(String owner, String repo) {
        return githubClient.get()
            .uri("/repos/{owner}/{repo}/issues", owner, repo)
            .retrieve()
            .bodyToFlux(Issue.class);
    }
}
```

### REST Countries API
```java
@Service
public class CountryService {

    private final WebClient countriesClient;

    public CountryService() {
        this.countriesClient = WebClient.builder()
            .baseUrl("https://restcountries.com/v3.1")
            .build();
    }

    public Mono<Country> getCountryByName(String name) {
        return countriesClient.get()
            .uri("/name/{name}", name)
            .retrieve()
            .bodyToFlux(Country.class)
            .next();
    }

    public Flux<Country> getAllCountries() {
        return countriesClient.get()
            .uri("/all")
            .retrieve()
            .bodyToFlux(Country.class);
    }
}
```

## GraphQL Integration

### GraphQL Client Setup
```java
@Configuration
public class GraphQLConfig {

    @Bean
    public WebGraphQlClient webGraphQlClient() {
        return WebGraphQlClient.builder(
            WebClient.builder()
                .baseUrl("https://api.example.com/graphql")
                .build())
            .build();
    }
}

@Service
@RequiredArgsConstructor
public class GraphQLService {

    private final WebGraphQlClient graphQlClient;

    public Mono<User> getUser(String id) {
        String query = """
            query GetUser($id: ID!) {
                user(id: $id) {
                    id
                    name
                    email
                    createdAt
                }
            }
            """;

        return graphQlClient.document(query)
            .variable("id", id)
            .retrieve("user")
            .toEntity(User.class);
    }
}
```

## Caching External API Responses

### Redis Caching
```java
@Service
@RequiredArgsConstructor
public class CachedApiService {

    private final ExternalApiService apiService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Cacheable(value = "apiResponses", key = "#id", unless = "#result == null")
    public ApiResponse getCachedData(String id) {
        return apiService.getData(id).block();
    }

    @CacheEvict(value = "apiResponses", key = "#id")
    public void evictCache(String id) {
        // Cache will be evicted
    }

    @CachePut(value = "apiResponses", key = "#id")
    public ApiResponse refreshCache(String id) {
        return apiService.getData(id).block();
    }
}
```

### Cache Configuration
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000
      cache-null-values: false
  data:
    redis:
      host: localhost
      port: 6379
```

## Error Handling for External APIs

### Custom Exception Handling
```java
@ControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientError(WebClientResponseException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());

        ErrorResponse error = new ErrorResponse(
            status.value(),
            "External API error: " + ex.getMessage(),
            LocalDateTime.now()
        );

        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ErrorResponse> handleConnectionError(WebClientRequestException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "Unable to connect to external service",
            LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
}
```

## OAuth2 API Integration

### OAuth2 Client Configuration
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, profile, email
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: read:user, user:email
```

### OAuth2 WebClient
```java
@Configuration
public class OAuth2WebClientConfig {

    @Bean
    public WebClient oauth2WebClient(
            OAuth2AuthorizedClientManager authorizedClientManager) {

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2.setDefaultClientRegistrationId("google");

        return WebClient.builder()
            .apply(oauth2.oauth2Configuration())
            .build();
    }
}
```

## Async API Patterns

### CompletableFuture for Parallel Calls
```java
@Service
public class AggregatorService {

    @Async
    public CompletableFuture<UserProfile> getUserProfile(String userId) {
        CompletableFuture<User> userFuture = userService.getUser(userId);
        CompletableFuture<List<Order>> ordersFuture = orderService.getOrders(userId);
        CompletableFuture<List<Notification>> notifFuture = notificationService.getNotifications(userId);

        return CompletableFuture.allOf(userFuture, ordersFuture, notifFuture)
            .thenApply(v -> UserProfile.builder()
                .user(userFuture.join())
                .orders(ordersFuture.join())
                .notifications(notifFuture.join())
                .build());
    }
}
```

## API Versioning Strategies

### URL Path Versioning
```java
@RestController
@RequestMapping("/api/v1/users")
public class UserControllerV1 {
    // Version 1 endpoints
}

@RestController
@RequestMapping("/api/v2/users")
public class UserControllerV2 {
    // Version 2 endpoints with enhanced features
}
```

### Header-Based Versioning
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping(headers = "X-API-Version=1")
    public ResponseEntity<UserV1> getUserV1(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserV1(id));
    }

    @GetMapping(headers = "X-API-Version=2")
    public ResponseEntity<UserV2> getUserV2(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserV2(id));
    }
}
```

## Best Practices Summary
1. Always implement circuit breakers for external API calls
2. Use connection pooling and timeouts appropriately
3. Cache responses when possible to reduce load
4. Implement proper error handling and fallbacks
5. Use async patterns for parallel API calls
6. Log requests and responses for debugging
7. Secure API keys and tokens properly
8. Implement rate limiting on your side too
