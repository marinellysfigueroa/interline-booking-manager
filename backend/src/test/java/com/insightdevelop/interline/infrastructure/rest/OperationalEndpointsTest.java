package com.insightdevelop.interline.infrastructure.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Endpoints operativos: salud, métricas, contrato publicado y errores HTTP genéricos. */
@QuarkusTest
class OperationalEndpointsTest {

    @Test
    void readiness_checks_the_database() {
        given().get("/q/health/ready").then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem(containsString("Database")));
        given().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void exposes_prometheus_metrics() {
        given().get("/q/metrics").then().statusCode(200).body(containsString("http_server_requests"));
    }

    @Test
    void publishes_the_reviewed_contract() {
        given().queryParam("format", "json").get("/openapi").then().statusCode(200)
                .body("info.title", equalTo("Interline Booking Manager API"))
                .body("paths.'/api/v1/bookings/{locator}/ticket'.post.operationId", equalTo("issueTicket"));
    }

    @Test
    void invalid_enum_in_query_is_a_400_problem() {
        given().queryParam("status", "NOPE").get("/api/v1/bookings").then()
                .statusCode(400)
                .contentType("application/problem+json");
    }

    @Test
    void unknown_route_is_a_404_problem() {
        given().get("/api/v1/nope").then().statusCode(404).contentType("application/problem+json");
    }
}
