package com.example.buildtrigger.registry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.buildtrigger.model.Application;

class ApplicationRegistryTest {

    private ApplicationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ApplicationRegistry();
        registry.init(); // manually call @PostConstruct
    }

    @Test
    void testGetAllReturnsEntries() {
        assertFalse(registry.getAll().isEmpty(),
                "Registry should contain at least one application");
    }

    @Test
    void testFindByNameExists() {
        Application app = registry.findByName("sample-hello");
        assertNotNull(app, "sample-hello should exist in registry");
        assertEquals("sample-hello", app.getName());
        assertEquals("service", app.getType());
        assertFalse(app.getEnvironments().isEmpty());
    }

    @Test
    void testFindByNameNotFound() {
        Application app = registry.findByName("does-not-exist");
        assertNull(app, "Non-existent app should return null");
    }

    @Test
    void testApplicationHasEnvironments() {
        Application app = registry.findByName("order-management");
        assertNotNull(app);
        assertTrue(app.getEnvironments().contains("dev"));
        assertTrue(app.getEnvironments().contains("prod"));
    }

    @Test
    void testApplicationHasServices() {
        Application app = registry.findByName("order-management");
        assertNotNull(app);
        assertFalse(app.getServices().isEmpty(),
                "order-management should have services defined");
    }
}
