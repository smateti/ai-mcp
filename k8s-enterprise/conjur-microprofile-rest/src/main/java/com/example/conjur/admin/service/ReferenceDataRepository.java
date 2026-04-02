package com.example.conjur.admin.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ReferenceDataRepository {

    private static final Logger LOG = Logger.getLogger(ReferenceDataRepository.class.getName());

    @Inject
    @ConfigProperty(name = "refdata.db.path", defaultValue = "./refdata")
    private String dbPath;

    private String jdbcUrl;

    @PostConstruct
    void init() {
        jdbcUrl = "jdbc:h2:file:" + dbPath + ";AUTO_SERVER=TRUE";
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            LOG.severe("H2 driver not found: " + e.getMessage());
            return;
        }
        createTable();
        seedDefaults();
        LOG.info("ReferenceDataRepository initialized at " + dbPath);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    private void createTable() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ref_item (
                    id IDENTITY PRIMARY KEY,
                    type VARCHAR(50) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    description VARCHAR(255) DEFAULT '',
                    fields VARCHAR(1000) DEFAULT '',
                    UNIQUE(type, name)
                )
            """);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to create ref_item table", e);
        }
    }

    private void seedDefaults() {
        try (Connection conn = getConnection()) {
            // Check if already seeded
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM ref_item");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) return;
            }

            // Environments
            insertSeed(conn, "environments", "dev", "Development environment");
            insertSeed(conn, "environments", "qa", "QA / Testing environment");
            insertSeed(conn, "environments", "staging", "Staging / Pre-production");
            insertSeed(conn, "environments", "prod", "Production environment");

            // App Types
            insertSeed(conn, "apptypes", "nims", "NIMS application");
            insertSeed(conn, "apptypes", "batch", "Batch processing");
            insertSeed(conn, "apptypes", "springboot", "Spring Boot application");
            insertSeed(conn, "apptypes", "quarkus", "Quarkus application");
            insertSeed(conn, "apptypes", "openshift", "OpenShift workload");
            insertSeed(conn, "apptypes", "kubernetes", "Kubernetes workload");

            // Resource Types (with field definitions)
            insertSeedWithFields(conn, "resourcetypes", "dbs", "Database",
                    "host-name,username,password,port,database-name");
            insertSeedWithFields(conn, "resourcetypes", "kafka", "Kafka cluster",
                    "bootstrap-servers,sasl-username,sasl-password,sasl-mechanism,security-protocol,keystore-password,truststore-password,schema-registry-url,schema-registry-key,schema-registry-secret");
            insertSeedWithFields(conn, "resourcetypes", "api", "API credentials",
                    "key,secret,webhook-secret");
            insertSeedWithFields(conn, "resourcetypes", "smtp", "SMTP mail server",
                    "host,port,username,password,from-address");
            insertSeedWithFields(conn, "resourcetypes", "ldap", "LDAP directory",
                    "url,base-dn,bind-dn,bind-password");
            insertSeedWithFields(conn, "resourcetypes", "oauth", "OAuth provider",
                    "tenant-id,client-id,client-secret,token-endpoint");
            insertSeedWithFields(conn, "resourcetypes", "certs", "Certificate store",
                    "keystore-password,truststore-password,keystore-type");

            LOG.info("Seeded default reference data");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Seed failed (may already exist)", e);
        }
    }

    private void insertSeed(Connection conn, String type, String name, String description) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ref_item (type, name, description, fields) VALUES (?, ?, ?, '')")) {
            ps.setString(1, type);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.executeUpdate();
        }
    }

    private void insertSeedWithFields(Connection conn, String type, String name, String description, String fields) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ref_item (type, name, description, fields) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, type);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setString(4, fields);
            ps.executeUpdate();
        }
    }

    // ===== CRUD Operations =====

    public List<Map<String, Object>> list(String type) {
        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, description, fields FROM ref_item WHERE type = ? ORDER BY name")) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(rowToMap(rs));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "List failed for type: " + type, e);
        }
        return items;
    }

    public Map<String, Object> create(String type, String name, String description, String fields) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ref_item (type, name, description, fields) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, name);
            ps.setString(3, description != null ? description : "");
            ps.setString(4, fields != null ? fields : "");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return Map.of("id", keys.getLong(1), "name", name, "type", type, "status", "created");
                }
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Unique index")) {
                return Map.of("error", "Item '" + name + "' already exists in " + type);
            }
            LOG.log(Level.WARNING, "Create failed", e);
            return Map.of("error", e.getMessage());
        }
        return Map.of("error", "Unknown error");
    }

    public Map<String, Object> update(long id, String name, String description, String fields) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ref_item SET name = ?, description = ?, fields = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, description != null ? description : "");
            ps.setString(3, fields != null ? fields : "");
            ps.setLong(4, id);
            int rows = ps.executeUpdate();
            if (rows > 0) return Map.of("id", id, "status", "updated");
            return Map.of("error", "Item not found");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Update failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> delete(long id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM ref_item WHERE id = ?")) {
            ps.setLong(1, id);
            int rows = ps.executeUpdate();
            if (rows > 0) return Map.of("id", id, "status", "deleted");
            return Map.of("error", "Item not found");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Delete failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, List<Map<String, Object>>> getAll() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        result.put("organizations", new ArrayList<>());
        result.put("environments", new ArrayList<>());
        result.put("products", new ArrayList<>());
        result.put("apptypes", new ArrayList<>());
        result.put("resourcetypes", new ArrayList<>());

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, type, name, description, fields FROM ref_item ORDER BY type, name")) {
            while (rs.next()) {
                String type = rs.getString("type");
                Map<String, Object> item = rowToMap(rs);
                result.computeIfAbsent(type, k -> new ArrayList<>()).add(item);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "GetAll failed", e);
        }
        return result;
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("name", rs.getString("name"));
        m.put("description", rs.getString("description"));
        String fields = rs.getString("fields");
        if (fields != null && !fields.isEmpty()) {
            m.put("fields", fields);
        }
        return m;
    }
}
