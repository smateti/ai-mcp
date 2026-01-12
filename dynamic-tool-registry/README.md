# Dynamic Tool Registry

A Spring Boot web application that dynamically registers tools by parsing OpenAPI specifications. This application allows you to input a tool ID and OpenAPI endpoint, automatically parses the schema, and provides a web form to enter custom descriptions for each input/output parameter.

## Features

- **OpenAPI Parsing**: Automatically parses OpenAPI 3.0 specifications
- **Dynamic Tool Registration**: Register API endpoints as tools with custom tool IDs
- **Web Forms**: Interactive web interface to view and edit tool descriptions
- **H2 Database**: In-memory database for storing tool definitions
- **Parameter Management**: Edit descriptions for all parameters and responses
- **REST API**: Full REST API for programmatic access

## Technology Stack

- Java 17
- Spring Boot 3.2.1
- Spring Data JPA
- H2 Database
- Thymeleaf
- Swagger Parser (OpenAPI)
- Lombok

## Project Structure

```
dynamic-tool-registry/
├── src/main/java/com/example/toolregistry/
│   ├── DynamicToolRegistryApplication.java  # Main application
│   ├── controller/
│   │   ├── HomeController.java              # Home page redirect
│   │   ├── ToolApiController.java           # REST API endpoints
│   │   └── ToolWebController.java           # Web form controllers
│   ├── entity/
│   │   ├── ToolDefinition.java              # Tool entity
│   │   ├── ParameterDefinition.java         # Parameter entity
│   │   └── ResponseDefinition.java          # Response entity
│   ├── repository/
│   │   ├── ToolDefinitionRepository.java
│   │   ├── ParameterDefinitionRepository.java
│   │   └── ResponseDefinitionRepository.java
│   ├── service/
│   │   ├── OpenApiParserService.java        # OpenAPI parsing logic
│   │   └── ToolRegistrationService.java     # Business logic
│   └── dto/
│       ├── ParsedToolInfo.java
│       ├── ParameterInfo.java
│       ├── ResponseInfo.java
│       └── ToolRegistrationRequest.java
├── src/main/resources/
│   ├── templates/tools/                     # Thymeleaf templates
│   │   ├── list.html
│   │   ├── register.html
│   │   ├── preview.html
│   │   ├── view.html
│   │   └── edit.html
│   ├── static/css/
│   │   └── style.css
│   └── application.properties
└── pom.xml
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+ (or use your IDE's built-in Maven)

### Building the Project

```bash
cd dynamic-tool-registry
mvn clean package
```

### Running the Application

```bash
java -jar target/dynamic-tool-registry-1.0.0.jar
```

Or using Maven:

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Usage

### Web Interface

1. **Access the application**: Open `http://localhost:8080` in your browser

2. **Register a new tool**:
   - Click "Register New Tool"
   - Enter:
     - **Tool ID**: Unique identifier (e.g., `petstore-get-pet`)
     - **OpenAPI Specification URL**: URL to OpenAPI spec (e.g., `https://petstore.swagger.io/v2/swagger.json`)
     - **API Path**: The specific endpoint path (e.g., `/pet/{petId}`)
     - **HTTP Method**: GET, POST, PUT, DELETE, or PATCH
   - Click "Preview Tool" to see parsed information
   - Review parameters, responses, and descriptions
   - Click "Confirm Registration" to save

3. **View registered tools**:
   - See all registered tools in the list
   - Click "View" to see full details
   - Click "Edit" to modify descriptions

4. **Edit tool descriptions**:
   - Update the main tool description
   - Customize descriptions for each parameter
   - Save changes

### Example: Registering Petstore API

**Tool ID**: `get-pet-by-id`
**OpenAPI Endpoint**: `https://petstore.swagger.io/v2/swagger.json`
**Path**: `/pet/{petId}`
**HTTP Method**: `GET`

The system will parse and display:
- Parameter: `petId` (integer, required, in path)
- Responses: 200 (successful), 400 (invalid ID), 404 (pet not found)

You can then add custom descriptions like:
- "Retrieves detailed information about a specific pet from the store database"

### REST API Endpoints

#### Register a Tool
```bash
POST /api/tools/register
Content-Type: application/json

{
  "toolId": "my-tool",
  "openApiEndpoint": "https://api.example.com/openapi.json",
  "path": "/api/users",
  "httpMethod": "GET"
}
```

#### Preview Tool (without saving)
```bash
POST /api/tools/preview
Content-Type: application/json

{
  "toolId": "my-tool",
  "openApiEndpoint": "https://api.example.com/openapi.json",
  "path": "/api/users",
  "httpMethod": "GET"
}
```

#### List All Tools
```bash
GET /api/tools
```

#### Get Tool by ID
```bash
GET /api/tools/{id}
```

#### Get Tool by Tool ID
```bash
GET /api/tools/by-tool-id/{toolId}
```

#### Update Tool Description
```bash
PUT /api/tools/{id}/description
Content-Type: application/json

{
  "description": "Updated description"
}
```

#### Update Parameter Description
```bash
PUT /api/tools/{id}/parameters/{parameterName}/description
Content-Type: application/json

{
  "description": "Updated parameter description"
}
```

#### Delete Tool
```bash
DELETE /api/tools/{id}
```

## H2 Console

Access the H2 database console at `http://localhost:8080/h2-console`

**Connection Details**:
- JDBC URL: `jdbc:h2:mem:toolregistry`
- Username: `sa`
- Password: (leave empty)

## Database Schema

### tool_definitions
- `id` (PK)
- `tool_id` (unique)
- `name`
- `description`
- `open_api_endpoint`
- `http_method`
- `path`
- `created_at`
- `updated_at`

### parameter_definitions
- `id` (PK)
- `tool_definition_id` (FK)
- `name`
- `description`
- `type`
- `required`
- `in` (query, path, body, header)
- `format`
- `example`
- `default_value`

### response_definitions
- `id` (PK)
- `tool_definition_id` (FK)
- `status_code`
- `description`
- `type`
- `schema`

## Example OpenAPI Specifications

Here are some public OpenAPI specs you can use for testing:

1. **Petstore API**: `https://petstore.swagger.io/v2/swagger.json`
2. **JSONPlaceholder**: `https://jsonplaceholder.typicode.com/`
3. Your own API with OpenAPI documentation

## Configuration

Edit `src/main/resources/application.properties` to customize:

```properties
# Server port
server.port=8080

# Database settings
spring.datasource.url=jdbc:h2:mem:toolregistry

# Enable/disable H2 console
spring.h2.console.enabled=true
```

## Development

### Adding New Features

The application follows a standard Spring Boot architecture:
- **Controllers**: Handle HTTP requests
- **Services**: Business logic
- **Repositories**: Database access
- **Entities**: Data models
- **DTOs**: Data transfer objects

### Running Tests

```bash
mvn test
```

## License

MIT License

## Support

For issues or questions, please check the application logs or H2 console for debugging.
