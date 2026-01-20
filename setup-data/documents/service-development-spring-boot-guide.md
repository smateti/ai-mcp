# Spring Boot Microservices Development Guide

## Introduction to Spring Boot
Spring Boot is an open-source Java-based framework used to create production-ready microservices. It simplifies the development of Spring applications by providing auto-configuration, embedded servers, and opinionated defaults.

## Key Features
- **Auto-configuration**: Automatically configures your application based on the dependencies you add
- **Embedded Servers**: Includes Tomcat, Jetty, or Undertow directly
- **Production-ready features**: Health checks, metrics, and externalized configuration
- **No code generation**: No requirement for XML configuration

## Project Structure
A typical Spring Boot microservice follows this structure:
```
src/
├── main/
│   ├── java/
│   │   └── com/example/service/
│   │       ├── Application.java
│   │       ├── controller/
│   │       ├── service/
│   │       ├── repository/
│   │       ├── entity/
│   │       └── dto/
│   └── resources/
│       ├── application.yml
│       └── application-{profile}.yml
└── test/
    └── java/
```

## Creating a REST Controller
```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserDTO> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDTO createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }
}
```

## Service Layer Best Practices
- Use `@Service` annotation for business logic classes
- Implement interfaces for better testability
- Use `@Transactional` for database operations
- Handle exceptions at service layer with custom exceptions

## Repository Layer with Spring Data JPA
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByActiveTrue();

    @Query("SELECT u FROM User u WHERE u.createdAt > :date")
    List<User> findRecentUsers(@Param("date") LocalDateTime date);
}
```

## Configuration Management
Use application.yml for configuration:
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: ${DB_USER}
    password: ${DB_PASS}
  jpa:
    hibernate:
      ddl-auto: validate

app:
  feature:
    cache-enabled: true
    timeout-seconds: 30
```

## Error Handling
Implement global exception handling:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
        return new ErrorResponse("VALIDATION_ERROR", errors);
    }
}
```

## Health Checks and Actuator
Enable Spring Boot Actuator for production monitoring:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

## Testing
Write comprehensive tests:
```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnAllUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }
}
```

## Common Dependencies
- spring-boot-starter-web: REST APIs
- spring-boot-starter-data-jpa: Database access
- spring-boot-starter-validation: Input validation
- spring-boot-starter-actuator: Production features
- lombok: Reduce boilerplate code
