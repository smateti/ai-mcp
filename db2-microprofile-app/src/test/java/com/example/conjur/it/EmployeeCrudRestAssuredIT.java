package com.example.conjur.it;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.microshed.testing.SharedContainerConfig;
import org.microshed.testing.jupiter.MicroShedTest;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

/**
 * REST Assured integration tests for Employee CRUD using raw HTTP assertions.
 * MicroShed auto-configures REST Assured baseURI, port, and basePath (/api).
 */
@MicroShedTest
@SharedContainerConfig(AppContainerConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EmployeeCrudRestAssuredIT {

    private static int createdId;

    @Test
    @Order(1)
    public void testListEmployees() {
        given()
        .when()
            .get("/employees")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("$.size()", greaterThanOrEqualTo(3))
            .body("[0].firstName", notNullValue())
            .body("[0].salary", notNullValue());
    }

    @Test
    @Order(2)
    public void testCreateEmployee() {
        createdId =
            given()
                .contentType(JSON)
                .body("""
                    {
                        "firstName": "REST",
                        "lastName": "Assured",
                        "email": "rest.assured@example.com",
                        "department": "Testing",
                        "salary": 72000.00,
                        "hireDate": "2024-03-01"
                    }
                    """)
            .when()
                .post("/employees")
            .then()
                .statusCode(201)
                .contentType(JSON)
                .body("firstName", equalTo("REST"))
                .body("lastName", equalTo("Assured"))
                .body("salary", equalTo(72000.00f))
                .body("id", notNullValue())
            .extract()
                .path("id");
    }

    @Test
    @Order(3)
    public void testGetCreatedEmployee() {
        given()
        .when()
            .get("/employees/" + createdId)
        .then()
            .statusCode(200)
            .body("firstName", equalTo("REST"))
            .body("email", equalTo("rest.assured@example.com"));
    }

    @Test
    @Order(4)
    public void testUpdateEmployee() {
        given()
            .contentType(JSON)
            .body("""
                {
                    "firstName": "REST",
                    "lastName": "Assured-Updated",
                    "email": "rest.assured.updated@example.com",
                    "department": "Senior Testing",
                    "salary": 82000.00,
                    "hireDate": "2024-03-01"
                }
                """)
        .when()
            .put("/employees/" + createdId)
        .then()
            .statusCode(200)
            .body("lastName", equalTo("Assured-Updated"))
            .body("salary", equalTo(82000.00f));
    }

    @Test
    @Order(5)
    public void testDeleteEmployee() {
        given()
        .when()
            .delete("/employees/" + createdId)
        .then()
            .statusCode(204);

        // Verify 404
        given()
        .when()
            .get("/employees/" + createdId)
        .then()
            .statusCode(404);
    }

    @Test
    @Order(6)
    public void testGetNonExistentEmployee() {
        given()
        .when()
            .get("/employees/99999")
        .then()
            .statusCode(404)
            .contentType(JSON)
            .body("error", containsString("Employee not found"));
    }

    @Test
    @Order(7)
    public void testCreateEmployeeMinimal() {
        given()
            .contentType(JSON)
            .body("""
                {
                    "firstName": "Minimal",
                    "lastName": "Employee",
                    "email": "minimal@example.com",
                    "salary": 50000.00
                }
                """)
        .when()
            .post("/employees")
        .then()
            .statusCode(201)
            .body("firstName", equalTo("Minimal"));
    }
}
