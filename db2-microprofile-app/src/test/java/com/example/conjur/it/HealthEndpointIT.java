package com.example.conjur.it;

import org.junit.jupiter.api.Test;
import org.microshed.testing.SharedContainerConfig;
import org.microshed.testing.jupiter.MicroShedTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@MicroShedTest
@SharedContainerConfig(AppContainerConfig.class)
public class HealthEndpointIT {

    @Test
    public void testHealthEndpointIsUp() {
        given()
            .basePath("")
        .when()
            .get("/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.name", hasItems("app-alive", "database-connection"));
    }

    @Test
    public void testLivenessCheck() {
        given()
            .basePath("")
        .when()
            .get("/health/live")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.name", hasItem("app-alive"));
    }

    @Test
    public void testReadinessCheck() {
        given()
            .basePath("")
        .when()
            .get("/health/ready")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.name", hasItem("database-connection"))
            .body("checks.find { it.name == 'database-connection' }.data.database",
                    containsString("DB2"));
    }
}
