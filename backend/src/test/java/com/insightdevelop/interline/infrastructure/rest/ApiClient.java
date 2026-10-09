package com.insightdevelop.interline.infrastructure.rest;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cliente de pruebas sobre la API real (RestAssured), para expresar los flujos en pocas líneas. */
final class ApiClient {

    static final LocalDate DEPARTURE = LocalDate.now().plusDays(30);

    private ApiClient() {
    }

    static String searchOfferId(String origin, String destination, int adults, int infants) {
        return searchOffers(origin, destination, adults, infants)
                .statusCode(200)
                .extract().path("offers[0].offerId");
    }

    static ValidatableResponse searchOffers(String origin, String destination, int adults, int infants) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("origin", origin, "destination", destination, "departureDate", DEPARTURE.toString(),
                        "passengers", Map.of("adults", adults, "children", 0, "infants", infants)))
                .post("/api/v1/offers/search")
                .then();
    }

    /** Cuerpo de creación con {@code adults} adultos y {@code infants} infantes (cada uno con un adulto). */
    static Map<String, Object> bookingRequest(String offerId, int adults, int infants) {
        List<Map<String, Object>> passengers = new ArrayList<>();
        for (int i = 1; i <= adults; i++) {
            passengers.add(Map.of("ref", "A" + i, "type", "ADT", "firstName", "Adulto" + i, "lastName", "Prueba",
                    "dateOfBirth", "1985-03-1" + i));
        }
        for (int i = 1; i <= infants; i++) {
            passengers.add(Map.of("ref", "I" + i, "type", "INF", "firstName", "Infante" + i, "lastName", "Prueba",
                    "dateOfBirth", DEPARTURE.minusMonths(10).toString(), "associatedAdultRef", "A" + i));
        }
        return Map.of("offerId", offerId, "contact", Map.of("email", "qa@example.com"), "passengers", passengers);
    }

    static ValidatableResponse createBooking(Map<String, Object> body, String idempotencyKey) {
        return given().contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .post("/api/v1/bookings")
                .then();
    }

    static String createHeldBooking(String origin, String destination) {
        String offerId = searchOfferId(origin, destination, 1, 0);
        return createBooking(bookingRequest(offerId, 1, 0), newKey())
                .statusCode(201)
                .extract().path("locator");
    }

    static ValidatableResponse payCash(String locator, Object amount) {
        return given().contentType(ContentType.JSON)
                .header("Idempotency-Key", newKey())
                .body(Map.of("method", "CASH", "cash", Map.of("amount", amount, "currency", "USD")))
                .post("/api/v1/bookings/{locator}/payments", locator)
                .then();
    }

    /** Paga en efectivo exactamente la tarifa de la reserva. */
    static ValidatableResponse payFare(String locator) {
        Object amount = getBooking(locator).extract().path("fare.total.amount");
        return payCash(locator, amount);
    }

    static ValidatableResponse ticket(String locator) {
        return given().post("/api/v1/bookings/{locator}/ticket", locator).then();
    }

    static ValidatableResponse cancel(String locator) {
        return given().contentType(ContentType.JSON).body(Map.of("reason", "prueba"))
                .post("/api/v1/bookings/{locator}/cancel", locator).then();
    }

    static ValidatableResponse getBooking(String locator) {
        return given().get("/api/v1/bookings/{locator}", locator).then();
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }
}
