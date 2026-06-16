package com.example.hello;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS application root. All resources are served under /api.
 */
@ApplicationPath("/api")
public class HelloApplication extends Application {
}
