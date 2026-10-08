package com.insightdevelop.interline.infrastructure.rest;

import static com.insightdevelop.interline.infrastructure.rest.ApiClient.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.booking.SegmentStatus;
import com.insightdevelop.interline.domain.port.PaymentGateway;
import com.insightdevelop.interline.domain.port.SegmentInventoryGateway;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Saga de emisión con compensación. Los sistemas externos se sustituyen con
 * {@code @InjectMock} (≈ {@code @MockitoBean} de Spring): el mock reemplaza al bean CDI solo
 * en esta clase de prueba.
 */
@QuarkusTest
class TicketingSagaCompensationTest {

    @InjectMock
    SegmentInventoryGateway inventory;

    @InjectMock
    PaymentGateway paymentGateway;

    @BeforeEach
    void operatorsRespond() {
        // AV confirma al vender; IB responde UC y sigue UC en todas las consultas
        when(inventory.sell(any(), argThat(carrier("AV")), anyInt())).thenReturn(SegmentStatus.HK);
        when(inventory.sell(any(), argThat(carrier("IB")), anyInt())).thenReturn(SegmentStatus.UC);
        when(inventory.checkStatus(any(), argThat(carrier("IB")))).thenReturn(SegmentStatus.UC);
        when(paymentGateway.authorize(any(), any(), anyString())).thenReturn("AUTH-TEST-1");
    }

    @Test
    void segment_still_uc_after_retries_compensates_and_fails_the_booking() {
        String locator = createHeldBooking("BOG", "FCO");
        payFare(locator).statusCode(201);

        ticket(locator).statusCode(422)
                .contentType("application/problem+json")
                .body("code", equalTo("SEGMENT_NOT_CONFIRMED"))
                .body("locator", equalTo(locator))
                .body("bookingStatus", equalTo("FAILED"))
                .body("detail", containsString("IB3234 MAD-FCO"));

        // 3 consultas (max-attempts) antes de rendirse
        verify(inventory, times(3)).checkStatus(any(), argThat(carrier("IB")));
        // Compensación: se cancelan el segmento confirmado y el no confirmado, y se libera el pago
        verify(inventory).cancel(any(), argThat(carrier("AV")));
        verify(inventory).cancel(any(), argThat(carrier("IB")));
        verify(paymentGateway).release("AUTH-TEST-1");
        verify(paymentGateway, never()).capture(anyString());

        getBooking(locator).statusCode(200)
                .body("status", equalTo("FAILED"))
                .body("segments.status", everyItem(equalTo("XX")))
                .body("payments[0].status", equalTo("RELEASED"))
                .body("tickets", empty())
                .body("statusHistory[-1].to", equalTo("FAILED"));
    }

    @Test
    void retrying_the_ticket_on_a_failed_booking_is_rejected_without_new_side_effects() {
        String locator = createHeldBooking("BOG", "FCO");
        payFare(locator).statusCode(201);
        ticket(locator).statusCode(422);

        ticket(locator).statusCode(409).body("code", equalTo("INVALID_STATE_TRANSITION"));

        verify(paymentGateway, times(1)).release("AUTH-TEST-1");
    }

    @Test
    void operator_rejection_at_creation_compensates_immediately() {
        when(inventory.sell(any(), argThat(carrier("IB")), anyInt())).thenReturn(SegmentStatus.XX);
        String offerId = searchOfferId("BOG", "FCO", 1, 0);

        String locator = createBooking(bookingRequest(offerId, 1, 0), newKey())
                .statusCode(422)
                .body("code", equalTo("SEGMENT_REJECTED"))
                .body("bookingStatus", equalTo("FAILED"))
                .extract().path("locator");

        verify(inventory).cancel(any(), argThat(carrier("AV")));
        getBooking(locator).body("status", equalTo("FAILED"))
                .body("segments.status", everyItem(equalTo("XX")));
    }

    private static org.mockito.ArgumentMatcher<Segment> carrier(String code) {
        return segment -> segment != null && segment.operatingCarrier().value().equals(code);
    }
}
