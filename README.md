# OpenLiberty Java EE 8 Demo Projects

Three multi-module Maven projects demonstrating EJB, Servlet, JDBC, and Remote EJB/IIOP on OpenLiberty 24.0.0.6 with Java 17+.

## Projects at a Glance

| Project | Purpose | HTTP Port | IIOP Port | Key Feature |
|---------|---------|-----------|-----------|-------------|
| **test4** | Stateless EJB + H2 JDBC + REST API | 9083 | 2809 | Remote EJB server with CRUD |
| **test5** | Web frontend for test4 | 9082 | 2810 | Remote EJB client via IIOP |
| **test6** | Stateful EJB shopping cart | 9081 | -- | Conversational state + lifecycle |

All projects use **Java EE 8** (`javax.*` namespace), **Java 17** compiler target, and **OpenLiberty 24.0.0.6**.

---

## test4 - Stateless Remote EJB with H2 JDBC

A stateless session bean that performs CRUD operations on an H2 database, exposed both as a remote EJB (via IIOP) and as a REST API (via JAX-RS).

### Module Structure

```
test4/
├── pom.xml                        (parent POM)
├── test4ejb/                      (EJB module, packaging: ejb)
│   ├── pom.xml
│   └── src/main/java/com/example/test4/
│       ├── ejb/
│       │   ├── Employee.java          Serializable POJO
│       │   ├── EmployeeBean.java      @Stateless EJB, plain JDBC
│       │   └── EmployeeRemote.java    @Remote interface
│       └── rest/
│           ├── RestApplication.java   @ApplicationPath("/api")
│           └── EmployeeResource.java  @Path("/employees") JAX-RS resource
└── test4ear/                      (EAR module)
    ├── pom.xml
    └── src/main/liberty/config/
        ├── server.xml
        └── jvm.options
```

### Key Components

**EmployeeBean** (`@Stateless`) - Uses `@Resource(lookup = "jdbc/h2DB")` to inject an H2 DataSource. Creates the `EMPLOYEE` table on `@PostConstruct`. All persistence is plain JDBC with `PreparedStatement` (no JPA).

**EmployeeRemote** (`@Remote`) - Exposes five methods: `createEmployee()`, `findEmployee()`, `findAllEmployees()`, `updateEmployee()`, `deleteEmployee()`.

**EJB Client JAR** - `maven-ejb-plugin` generates a client JAR (`test4ejb-1.0-SNAPSHOT-client.jar`) containing `EmployeeRemote` and `Employee` classes. This client JAR is used by test5 to compile and invoke the remote EJB.

**REST API** - JAX-RS resource at `/api/employees` provides an alternative HTTP/JSON interface to the same EJB.

### Liberty Configuration

| Feature | Purpose |
|---------|---------|
| `ejbRemote-3.2` | Remote EJB support (includes IIOP transport via Yoko ORB) |
| `jaxrs-2.1` | JAX-RS REST endpoints |
| `jsonp-1.1` | JSON Processing for REST serialization |
| `jndi-1.0` | JNDI lookups |
| `jdbc-4.2` | JDBC DataSource management |

**Ports**: HTTP 9083, HTTPS 9446, IIOP 2809

**DataSource**: H2 file-based database at `${server.output.dir}/h2data/test4db` with `AUTO_SERVER=TRUE`.

**JVM Options**: `--add-opens` flags for 9 JDK modules, required by OpenLiberty's Yoko ORB on Java 17+ (CORBA was removed from the JDK in Java 11, but OpenLiberty bundles its own Yoko ORB implementation).

### EJB JNDI Bindings

When test4 starts, Liberty binds the remote EJB at three locations:

```
ejb/test4ear/test4ejb.jar/EmployeeBean#com.example.test4.ejb.EmployeeRemote
com.example.test4.ejb.EmployeeRemote
java:global/test4ear/test4ejb/EmployeeBean!com.example.test4.ejb.EmployeeRemote
```

The CORBA name server publishes these at `corbaloc:iiop:localhost:2809/NameService`.

### Build & Run

```bash
cd test4
mvn clean install
cd test4ear
mvn liberty:run
```

Verify: `http://localhost:9083/test4ear/api/employees`

---

## test5 - Web Frontend via Remote EJB/IIOP

A thin web tier that calls test4's `EmployeeBean` remotely over IIOP. Demonstrates cross-server EJB invocation between two separate Liberty instances.

### Module Structure

```
test5/
├── pom.xml                        (parent POM)
├── test5web/                      (WAR module)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/test5/web/
│       │   └── EmployeeServlet.java   @WebServlet, JNDI/IIOP client
│       └── webapp/
│           ├── index.html
│           └── WEB-INF/web.xml
└── test5ear/                      (EAR module)
    ├── pom.xml
    └── src/main/liberty/config/
        ├── server.xml
        └── jvm.options
```

### Key Components

**EmployeeServlet** (`@WebServlet("/employees")`) - Performs a JNDI lookup via CORBA naming to obtain a remote reference to test4's `EmployeeBean`. Renders an HTML page with employee table, add form, and delete links.

**JNDI Lookup** - Uses the `corbaname` URL format:

```
corbaname::localhost:2809#ejb/global/test4ear/test4ejb/EmployeeBean!com.example.test4.ejb.EmployeeRemote
```

**PortableRemoteObject.narrow()** - Required to convert the CORBA stub into a typed proxy implementing `EmployeeRemote`. Since `javax.rmi` was removed from the JDK in Java 11, the servlet invokes `narrow()` via reflection (OpenLiberty provides the class at runtime through the `ejbRemote-3.2` feature).

**EJB Client JAR** - `test4ejb-1.0-SNAPSHOT-client.jar` is bundled in the WAR's `WEB-INF/lib/` (compile scope in test5web) rather than in the EAR's `lib/` directory. This is necessary because Liberty's loose application deployment does not honor `defaultLibBundleDir` for EAR-level libraries.

### Liberty Configuration

| Feature | Purpose |
|---------|---------|
| `servlet-4.0` | Web container for the servlet |
| `ejbRemote-3.2` | IIOP client ORB for remote EJB calls |
| `jndi-1.0` | JNDI context for CORBA naming lookups |

**Ports**: HTTP 9082, HTTPS 9445, IIOP 2810

**ORB Config**: `<orb nameService="corbaname::localhost:2809"/>` points the client ORB to test4's CosNaming service.

**IIOP Endpoint**: `<iiopEndpoint iiopPort="2810"/>` gives test5 its own ORB port (distinct from test4's 2809). The `ejbRemote-3.2` feature requires a functioning ORB, which needs its own IIOP endpoint even on the client side.

### Architecture

```
Browser
  │
  │ HTTP (port 9082)
  ▼
┌──────────────────────┐
│  test5Server         │
│  EmployeeServlet     │
│  (test5web.war)      │
│                      │
│  JNDI lookup ────────┼──── IIOP/CORBA (port 2809) ────►  ┌──────────────────────┐
│  narrow()            │                                     │  test4Server         │
│  employeeService     │                                     │  EmployeeBean        │
│    .findAll()        │◄──── serialized Employee[] ─────── │  (test4ejb.jar)      │
│    .create(...)      │                                     │       │              │
│    .delete(id)       │                                     │       ▼              │
│                      │                                     │  H2 Database         │
└──────────────────────┘                                     │  (jdbc/h2DB)         │
                                                             └──────────────────────┘
```

### Build & Run

test4 must be built and running first (test5 depends on `test4ejb` client JAR and test4's IIOP endpoint).

```bash
# Terminal 1 - build and start test4
cd test4
mvn clean install
cd test4ear
mvn liberty:run
# Wait for: "CWWKI0001I: The CORBA name server is now available at corbaloc:iiop:localhost:2809"

# Terminal 2 - build and start test5
cd test5
mvn clean install
cd test5ear
mvn liberty:run
```

Verify: `http://localhost:9082/test5web/employees`

---

## test6 - Stateful EJB Shopping Cart

A stateful session bean shopping cart demonstrating conversational state, EJB lifecycle callbacks, and the `@Remove` pattern.

### Module Structure

```
test6/
├── pom.xml                        (parent POM)
├── test6ejb/                      (EJB module, packaging: ejb)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/test6/ejb/
│       │   ├── CartItem.java              Serializable item model
│       │   ├── ShoppingCartLocal.java     @Local interface
│       │   └── ShoppingCart.java          @Stateful bean
│       └── resources/META-INF/
│           └── ejb-jar.xml
├── test6web/                      (WAR module)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/test6/web/
│       │   └── ShoppingCartServlet.java   @WebServlet("/cart")
│       └── webapp/
│           ├── index.html
│           └── WEB-INF/web.xml
└── test6ear/                      (EAR module)
    ├── pom.xml
    └── src/main/liberty/config/
        └── server.xml
```

### Key Components

**ShoppingCart** (`@Stateful`) - Maintains a `List<CartItem>` as per-client conversational state. Each client session gets its own bean instance with an isolated cart. The container preserves this state across multiple HTTP requests from the same client.

**Lifecycle Callbacks**:

| Annotation | Method | When Called |
|------------|--------|-------------|
| `@PostConstruct` | `init()` | Bean created for new client session |
| `@PrePassivate` | `passivate()` | Before container serializes bean to disk |
| `@PostActivate` | `activate()` | After container deserializes bean from disk |
| `@PreDestroy` | `cleanup()` | Before bean instance is garbage collected |
| `@Remove` | `checkout()` | Triggers bean destruction after method returns |

**Cart Operations**:

| Method | Behavior |
|--------|----------|
| `addItem(name, price, qty)` | Adds new item or updates quantity if item already exists |
| `removeItem(name)` | Removes item by name |
| `updateQuantity(name, qty)` | Changes quantity; removes item if qty <= 0 |
| `getCartContents()` | Returns unmodifiable copy of cart items |
| `getCartTotal()` | Sums all item subtotals (price x quantity) |
| `clearCart()` | Empties the cart |
| `checkout()` | Finalizes order and destroys the bean instance (`@Remove`) |

**ShoppingCartServlet** (`@WebServlet("/cart")`) - Injects the stateful EJB via `@EJB`. Handles actions via query parameters (`?action=add&name=X&price=Y&qty=Z`). Renders an HTML table of cart contents with add/remove/checkout controls.

### Liberty Configuration

| Feature | Purpose |
|---------|---------|
| `ejbLite-3.2` | Stateful/Stateless session bean support (local only, no IIOP) |
| `servlet-4.0` | Web container |
| `jndi-1.0` | JNDI lookups |

**Ports**: HTTP 9081, HTTPS 9444

### Stateful EJB Flow

```
1. First request  ──►  Container creates new ShoppingCart instance
                        @PostConstruct init() → generates cartId, empty list

2. Add items      ──►  Same instance reused (conversational state)
                        cartItems list grows across requests

3. (Optional)     ──►  Container may passivate bean (@PrePassivate)
                        and reactivate later (@PostActivate)

4. Checkout       ──►  checkout() returns order summary
                        @Remove tells container to destroy bean
                        @PreDestroy cleanup() runs
                        Next request creates a fresh instance
```

### Build & Run

```bash
cd test6
mvn clean install
cd test6ear
mvn liberty:run
```

Verify: `http://localhost:9081/test6web/cart`

---

## Common Configuration

### Prerequisites

- **JDK**: 17 or 21 (tested with OpenJDK 21.0.8)
- **Maven**: 3.8+
- **Ports**: 9081-9083 (HTTP), 9444-9446 (HTTPS), 2809-2810 (IIOP)

### Java EE 8 API

All projects use `javax:javaee-api:8.0.1` with `provided` scope (OpenLiberty supplies the runtime implementation).

### JVM Options (test4 and test5 only)

test4 and test5 include `jvm.options` files with `--add-opens` flags required by OpenLiberty's Yoko ORB for IIOP/Remote EJB on Java 17+:

```
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.security=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
--add-opens=java.base/sun.security.ssl=ALL-UNNAMED
--add-opens=java.naming/javax.naming.spi=ALL-UNNAMED
--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED
```

test6 does not need these flags because it uses `ejbLite-3.2` (local EJB only, no IIOP).

### Port Map

| Port | Protocol | Server | Service |
|------|----------|--------|---------|
| 9081 | HTTP | test6Server | Shopping cart web UI |
| 9082 | HTTP | test5Server | Employee manager web UI |
| 9083 | HTTP | test4Server | REST API + EJB server |
| 9444 | HTTPS | test6Server | |
| 9445 | HTTPS | test5Server | |
| 9446 | HTTPS | test4Server | |
| 2809 | IIOP | test4Server | CORBA name server (EJB host) |
| 2810 | IIOP | test5Server | Client ORB endpoint |

### Stopping Servers

```bash
cd <project>/test<N>ear
mvn liberty:stop
```

Or kill all Liberty processes:

```bash
taskkill /F /IM java.exe    # Windows
pkill -f wlp                # Linux/macOS
```

### Known Issues

- **Locked files on Windows**: If `mvn clean` fails with locked file errors, stop the Liberty server first (`mvn liberty:stop`) or kill the Java process.
- **FFDC on test5 startup**: A non-fatal `org.omg.CORBA.INITIALIZE` FFDC may appear during test5 startup. This is a harmless ORB initialization warning and does not affect functionality.
