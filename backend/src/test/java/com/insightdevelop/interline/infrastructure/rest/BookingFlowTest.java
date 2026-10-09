package com.insightdevelop.interline.infrastructure.rest;

import static com.insightdevelop.interline.infrastructure.rest.ApiClient.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Flujo completo contra la API real: búsqueda → reserva → pago → emisión, y sus errores. */
@QuarkusTest
class BookingFlowTest {

    @Test
    void happy_path_from_search_to_ticketed_single_ticket() {
        // BOG → FCO: AV26 BOG-MAD (operada por AV) + IB3234 MAD-FCO (operada por IB), validada por AV
        searchOffers("BOG", "FCO", 2, 1).statusCode(200)
                .body("count", greaterThan(0))
                .body("offers[0].validatingAirline", equalTo("AV"))
                .body("offers[0].interline.singleTicketEligible", equalTo(true))
                .body("offers[0].interline.operatingAirlines", contains("AV", "IB"));
        String offerId = searchOfferId("BOG", "FCO", 2, 1);

        String locator = createBooking(bookingRequest(offerId, 2, 1), newKey())
                .statusCode(201)
                .header("Location", containsString("/api/v1/bookings/"))
                .body("status", equalTo("HELD"))
                .body("passengers", hasSize(3))
                .body("segments.status", contains("HK", "UC")) // IB responde UC en el simulador
                .body("availableActions", containsInAnyOrder("PAY", "CANCEL"))
                .extract().path("locator");

        payFare(locator).statusCode(201)
                .body("status", equalTo("PAYMENT_AUTHORIZED"))
                .body("payments[0].status", equalTo("AUTHORIZED"));

        ticket(locator).statusCode(200)
                .body("status", equalTo("TICKETED"))
                .body("segments.status", everyItem(equalTo("HK")))
                .body("payments[0].status", equalTo("CAPTURED"))
                .body("tickets", hasSize(3))
                .body("tickets[0].number", startsWith("134"))
                .body("statusHistory.to", contains("DRAFT", "PRICED", "HELD", "PAYMENT_AUTHORIZED", "TICKETED"));

        // Emitir de nuevo es idempotente
        ticket(locator).statusCode(200).body("tickets", hasSize(3));

        getBooking(locator).statusCode(200)
                .body("status", equalTo("TICKETED"))
                .body("availableActions", empty());
    }

    @Test
    void miles_payment_masks_the_member_number() {
        String locator = createHeldBooking("BOG", "MAD");
        long miles = ((Number) getBooking(locator).extract().path("fare.milesEquivalent")).longValue();

        given().contentType(ContentType.JSON).header("Idempotency-Key", newKey())
                .body(Map.of("method", "MILES", "miles", Map.of("amount", miles, "memberNumber", "12345678901")))
                .post("/api/v1/bookings/{l}/payments", locator)
                .then().statusCode(201)
                .body("payments[0].method", equalTo("MILES"))
                .body("payments[0].miles.memberNumber", equalTo("*******8901"));
    }

    @Test
    void interline_rule_blocks_ticketing_without_changing_the_booking() {
        // LIM → MAD validada por LA con un tramo operado por AV: LA no tiene acuerdo con AV
        searchOffers("LIM", "MAD", 1, 0).statusCode(200)
                .body("offers[0].interline.singleTicketEligible", equalTo(false))
                .body("offers[0].interline.missingAgreements", contains("AV"));
        String locator = createHeldBooking("LIM", "MAD");
        payFare(locator).statusCode(201);

        ticket(locator).statusCode(422)
                .contentType("application/problem+json")
                .body("code", equalTo("INTERLINE_AGREEMENT_MISSING"))
                .body("bookingStatus", equalTo("PAYMENT_AUTHORIZED"));
        getBooking(locator).body("status", equalTo("PAYMENT_AUTHORIZED")).body("tickets", empty());
    }

    @Test
    void cancelling_releases_resources_and_is_idempotent() {
        String locator = createHeldBooking("BOG", "MAD");
        payFare(locator).statusCode(201);

        cancel(locator).statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("segments.status", everyItem(equalTo("XX")))
                .body("payments[0].status", equalTo("RELEASED"));
        cancel(locator).statusCode(200).body("status", equalTo("CANCELLED"));
    }

    @Test
    void a_ticketed_booking_cannot_be_cancelled_409() {
        String locator = createHeldBooking("MAD", "FCO");
        payFare(locator).statusCode(201);
        ticket(locator).statusCode(200);

        cancel(locator).statusCode(409)
                .body("code", equalTo("INVALID_STATE_TRANSITION"))
                .body("bookingStatus", equalTo("TICKETED"));
    }

    @Test
    void skipping_steps_is_a_409() {
        String locator = createHeldBooking("BOG", "MAD");

        ticket(locator).statusCode(409).body("code", equalTo("INVALID_STATE_TRANSITION"));
    }

    @Test
    void unknown_locator_is_a_404_problem() {
        getBooking("ZZZZZZ").statusCode(404)
                .contentType("application/problem+json")
                .body("code", equalTo("BOOKING_NOT_FOUND"))
                .body("instance", equalTo("/api/v1/bookings/ZZZZZZ"));
    }

    @Test
    void infant_rule_is_a_422() {
        String offerId = searchOfferId("BOG", "MAD", 1, 1);
        var body = bookingRequest(offerId, 1, 0);
        @SuppressWarnings("unchecked")
        var passengers = new java.util.ArrayList<>((java.util.List<Map<String, Object>>) body.get("passengers"));
        passengers.add(Map.of("ref", "I1", "type", "INF", "firstName", "Sofía", "lastName", "Prueba",
                "dateOfBirth", DEPARTURE.minusMonths(6).toString(), "associatedAdultRef", "A1"));
        passengers.add(Map.of("ref", "I2", "type", "INF", "firstName", "Mía", "lastName", "Prueba",
                "dateOfBirth", DEPARTURE.minusMonths(6).toString(), "associatedAdultRef", "A1"));

        createBooking(Map.of("offerId", offerId, "contact", Map.of("email", "qa@example.com"),
                "passengers", passengers), newKey())
                .statusCode(422)
                .body("code", equalTo("INFANT_RULE_VIOLATION"));
    }

    @Test
    void passengers_must_match_the_priced_offer() {
        String offerId = searchOfferId("BOG", "MAD", 2, 0);

        createBooking(bookingRequest(offerId, 1, 0), newKey())
                .statusCode(422).body("code", equalTo("PASSENGER_MISMATCH"));
    }

    @Test
    void insufficient_payment_is_a_422_and_malformed_payment_a_400() {
        String locator = createHeldBooking("BOG", "MAD");

        payCash(locator, 1.00).statusCode(422)
                .body("code", equalTo("INSUFFICIENT_PAYMENT"))
                .body("bookingStatus", equalTo("HELD"));

        given().contentType(ContentType.JSON).header("Idempotency-Key", newKey())
                .body(Map.of("method", "CASH"))
                .post("/api/v1/bookings/{l}/payments", locator)
                .then().statusCode(400)
                .body("errors[0].field", equalTo("cash"));
    }

    @Test
    void invalid_body_lists_every_field_error() {
        given().contentType(ContentType.JSON).header("Idempotency-Key", newKey())
                .body(Map.of("offerId", "X", "contact", Map.of("email", "no-es-email"),
                        "passengers", java.util.List.of(Map.of("ref", "A1", "type", "ADT", "firstName", "",
                                "lastName", "Prueba", "dateOfBirth", "1990-01-01"))))
                .post("/api/v1/bookings")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.field", hasItems("contact.email", "passengers[0].firstName"));
    }

    @Test
    void malformed_json_is_a_400() {
        given().contentType(ContentType.JSON).header("Idempotency-Key", newKey())
                .body("{\"offerId\": ")
                .post("/api/v1/bookings")
                .then().statusCode(400)
                .contentType("application/problem+json");
    }

    @Test
    void lists_bookings_filtered_by_status_and_tenant_with_pagination() {
        String held = createHeldBooking("BOG", "MAD");

        given().header("X-Tenant", "AV").queryParam("status", "HELD").queryParam("size", 100)
                .get("/api/v1/bookings")
                .then().statusCode(200)
                .body("items.locator", hasItem(held))
                .body("items.status", everyItem(equalTo("HELD")))
                .body("items.validatingAirline", everyItem(equalTo("AV")))
                .body("page", equalTo(0));

        given().header("X-Tenant", "LA").get("/api/v1/bookings")
                .then().statusCode(200).body("items.locator", not(hasItem(held)));

        given().queryParam("size", 1).get("/api/v1/bookings")
                .then().statusCode(200).body("items", hasSize(1)).body("size", equalTo(1));
    }
}
