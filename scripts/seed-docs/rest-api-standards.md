# REST API Design Standards

## URL Structure

### Resource Naming
- Use plural nouns: `/users`, `/orders`, `/products`
- Use lowercase with hyphens: `/order-items`, `/user-profiles`
- Avoid verbs in URLs (use HTTP methods instead)

### Hierarchy
- Represent relationships: `/users/{userId}/orders`
- Maximum nesting depth: 2 levels
- Use query parameters for filtering: `/orders?status=pending`

## HTTP Methods

### Standard Methods
- GET: Retrieve resource(s)
- POST: Create new resource
- PUT: Replace entire resource
- PATCH: Partial update
- DELETE: Remove resource

### Response Codes
- 200 OK: Successful GET/PUT/PATCH
- 201 Created: Successful POST
- 204 No Content: Successful DELETE
- 400 Bad Request: Invalid input
- 401 Unauthorized: Authentication required
- 403 Forbidden: Insufficient permissions
- 404 Not Found: Resource doesn't exist
- 409 Conflict: Resource conflict
- 500 Internal Server Error: Server error

## Request/Response Format

### Request Headers
- Content-Type: application/json
- Accept: application/json
- Authorization: Bearer {token}
- X-Request-ID: {uuid} (for tracing)

### Response Structure
```json
{
  "data": { ... },
  "meta": {
    "timestamp": "2024-01-15T10:30:00Z",
    "requestId": "abc-123"
  }
}
```

### Error Response
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input",
    "details": [
      { "field": "email", "message": "Invalid email format" }
    ]
  }
}
```

## Pagination

### Query Parameters
- page: Page number (0-based)
- size: Items per page (default: 20, max: 100)
- sort: Sort field and direction (e.g., `sort=createdAt,desc`)

### Response
Include page metadata with number, size, totalElements, and totalPages.

## Versioning
- Use URL path versioning: `/api/v1/users`
- Support previous version for 6 months after new release
- Document breaking changes in release notes
