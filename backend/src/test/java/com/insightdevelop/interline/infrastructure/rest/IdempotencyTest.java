package com.insightdevelop.interline.infrastructure.rest;

import static com.insightdevelop.interline.infrastructure.rest.ApiClient.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.insightdevelop.interline.domain.port.BookingRepository;
import com.insightdevelop.interline.domain.shared.PageRequest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IdempotencyTest {

    @Inject
    BookingRepository bookings;

    @Test
    void same_key_and_body_replays_the_original_response_without_creating_another_booking() {
        String offerId = searchOfferId("MAD", "FCO", 1, 0);
        var body = bookingRequest(offerId, 1, 0);
        String key = newKey();
        long before = countBookings();

        ExtractableResponse<Response> first = createBooking(body, key).statusCode(201)
                .header("Idempotency-Replayed", "false").extract();
        ExtractableResponse<Response> second = createBooking(body, key).statusCode(201)
                .header("Idempotency-Replayed", "true").extract();

        assertThat(second.<String>path("locator")).isEqualTo(first.path("locator"));
        assertThat(second.header("Location")).isEqualTo(first.header("Location"));
        assertThat(second.body().asString()).isEqualTo(first.body().asString());
        assertThat(countBookings()).isEqualTo(before + 1);
    }

    @Test
    void same_key_with_a_different_body_is_a_422() {
        String key = newKey();
        createBooking(bookingRequest(searchOfferId("MAD", "FCO", 1, 0), 1, 0), key).statusCode(201);

        createBooking(bookingRequest(searchOfferId("MAD", "FCO", 1, 0), 1, 0), key)
                .statusCode(422)
                .body("code", equalTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void business_errors_are_replayed_too() {
        String offerId = searchOfferId("MAD", "FCO", 2, 0);
        String key = newKey();

        createBooking(bookingRequest(offerId, 1, 0), key).statusCode(422)
                .body("code", equalTo("PASSENGER_MISMATCH"));
        createBooking(bookingRequest(offerId, 1, 0), key).statusCode(422)
                .header("Idempotency-Replayed", "true")
                .body("code", equalTo("PASSENGER_MISMATCH"));
    }

    @Test
    void the_header_is_required() {
        given().contentType(ContentType.JSON)
                .body(bookingRequest("OF-1", 1, 0))
                .post("/api/v1/bookings")
                .then().statusCode(400)
                .body("errors[0].field", equalTo("idempotencyKey"));
    }

    @Test
    void repeated_payment_with_the_same_key_authorizes_only_once() {
        String locator = createHeldBooking("BOG", "MAD");
        Object amount = getBooking(locator).extract().path("fare.total.amount");
        var body = Map.of("method", "CASH", "cash", Map.of("amount", amount, "currency", "USD"));
        String key = newKey();

        for (int i = 0; i < 2; i++) {
            given().contentType(ContentType.JSON).header("Idempotency-Key", key).body(body)
                    .post("/api/v1/bookings/{l}/payments", locator)
                    .then().statusCode(201).body("status", equalTo("PAYMENT_AUTHORIZED"));
        }
        getBooking(locator).body("payments.size()", equalTo(1));
    }

    long countBookings() {
        return QuarkusTransaction.requiringNew().call(() ->
                bookings.search(BookingRepository.Query.all(), new PageRequest(0, 1)).totalElements());
    }
}
