Target stack:
- Open Liberty (latest LTS), MicroProfile 6.x
- Java 17
- JPA 3.1 with h2 with file system(use @Entity, no native FoxPro DBF access) db2 can be in prod after everything tested
- CDI for everything; @ApplicationScoped services, @RequestScoped controllers
- JAX-RS for any REST endpoints; servlets + JSP for UI
- MicroProfile Config for all environment-specific values
- MicroProfile Health and Metrics on every service
- JSP with JSTL only, no scriptlets, no Struts/JSF
- Front controller servlet pattern: /app/<screen> dispatches to JSP
- Bean Validation (Jakarta Validation) on entities and DTOs
- SLF4J for logging
- Build: Maven, liberty-maven-plugin, deployable to OpenShift

Conventions:
- Packages: com.<org>.<app>.{entity,repository,service,web,dto,config}
- One JPA entity per DBF table; deleted-record handling via @Where or 
  explicit predicate (FoxPro SET DELETED semantics)
- All DB access through repository beans, never from servlets/JSPs
- Validation errors surface as request-scoped flash messages in JSP
- Date fields: java.time.LocalDate/LocalDateTime, never java.util.Date
- Money/numeric: BigDecimal with explicit scale