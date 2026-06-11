package com.example.hello;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for GreetingService.
 * Tests the greeting logic without requiring a running Liberty server.
 */
class GreetingServiceTest {

    @Test
    void greetShouldReturnFormattedMessage() {
        // GreetingService uses @Inject for config values, but we can test the
        // greet() logic by constructing an instance and setting fields via reflection.
        GreetingService service = new GreetingService();

        // Use reflection to set the injected fields (since there are no setters)
        try {
            var appNameField = GreetingService.class.getDeclaredField("appName");
            appNameField.setAccessible(true);
            appNameField.set(service, "test-app");

            var envField = GreetingService.class.getDeclaredField("environment");
            envField.setAccessible(true);
            envField.set(service, "test");

            var versionField = GreetingService.class.getDeclaredField("appVersion");
            versionField.setAccessible(true);
            versionField.set(service, "1.0.0");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Could not set fields via reflection: " + e.getMessage());
        }

        String result = service.greet("Alice");

        assertEquals("Hello, Alice! From test-app v1.0.0 [test]", result);
    }

    @Test
    void greetShouldHandleEmptyName() {
        GreetingService service = new GreetingService();

        try {
            var appNameField = GreetingService.class.getDeclaredField("appName");
            appNameField.setAccessible(true);
            appNameField.set(service, "my-app");

            var envField = GreetingService.class.getDeclaredField("environment");
            envField.setAccessible(true);
            envField.set(service, "dev");

            var versionField = GreetingService.class.getDeclaredField("appVersion");
            versionField.setAccessible(true);
            versionField.set(service, "2.0.0");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Could not set fields via reflection: " + e.getMessage());
        }

        String result = service.greet("");

        assertEquals("Hello, ! From my-app v2.0.0 [dev]", result);
    }
}
