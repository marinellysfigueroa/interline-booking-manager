package com.insightdevelop.interline.infrastructure.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * {@code @QuarkusTest} arranca la aplicación completa una sola vez para todas las clases de
 * prueba (≈ {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} con contexto cacheado) y
 * Dev Services levanta PostgreSQL en Docker automáticamente.
 */
@QuarkusTest
class AirportsResourceTest {

    @Test
    void finds_airport_by_code_first() {
        given().queryParam("query", "mad")
                .when().get("/api/v1/airports")
                .then().statusCode(200)
                .body("[0].code", equalTo("MAD"))
                .body("[0].city", equalTo("Madrid"));
    }

    @Test
    void search_ignores_accents_and_case() {
        given().queryParam("query", "BOGOTÁ")
                .when().get("/api/v1/airports")
                .then().statusCode(200)
                .body("code", hasItem("BOG"));
    }

    @Test
    void short_query_is_a_400_problem_with_field_errors() {
        given().queryParam("query", "m")
                .when().get("/api/v1/airports")
                .then().statusCode(400)
                .contentType("application/problem+json")
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors[0].field", equalTo("query"))
                .body("type", startsWith("https://interline.example.com/problems/"));
    }

    @Test
    void echoes_or_generates_the_correlation_id() {
        given().header("X-Correlation-ID", "test-123").queryParam("query", "lim")
                .when().get("/api/v1/airports")
                .then().statusCode(200).header("X-Correlation-ID", equalTo("test-123"));

        given().queryParam("query", "lim")
                .when().get("/api/v1/airports")
                .then().statusCode(200).header("X-Correlation-ID", notNullValue());
    }
}
