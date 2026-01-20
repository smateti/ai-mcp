# Spring Boot Security Configuration

## JWT Authentication

### Configuration
Configure SecurityFilterChain with:
- CSRF disabled for stateless APIs
- Session management set to STATELESS
- Public endpoints permitted (health, public APIs)
- Admin endpoints restricted to ADMIN role
- All other requests require authentication
- OAuth2 resource server with JWT decoder

### JWT Validation
- Validate signature against public key
- Check expiration (exp claim)
- Verify issuer (iss claim)
- Verify audience (aud claim)

## Role-Based Access Control

### Role Hierarchy
- ROLE_ADMIN: Full access
- ROLE_MANAGER: Read/write access
- ROLE_USER: Read access
- ROLE_GUEST: Limited public access

### Method Security
Use @PreAuthorize annotations:
- hasRole('ADMIN') for admin-only methods
- Combine with SpEL for owner-based access

## API Security Best Practices

### Input Validation
- Validate all input parameters
- Use Bean Validation annotations
- Sanitize user input
- Limit request body size

### Rate Limiting
- Implement per-user rate limits
- Use Redis for distributed counting
- Return 429 Too Many Requests

### CORS Configuration
Configure allowed origins, methods, and headers. Only allow trusted domains in production.

## Secrets Management
- Never commit secrets to version control
- Use environment variables or vault
- Rotate secrets regularly
- Audit secret access
