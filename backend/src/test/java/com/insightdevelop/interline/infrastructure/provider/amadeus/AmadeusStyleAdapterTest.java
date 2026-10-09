package com.insightdevelop.interline.infrastructure.provider.amadeus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.PassengerCounts;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.ItineraryDirection;
import com.insightdevelop.interline.domain.offer.OfferSearchCriteria;
import com.insightdevelop.interline.domain.offer.OfferSegment;
import com.insightdevelop.interline.domain.port.FlightOffersProvider;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Adaptador estilo Amadeus contra el Dev Service de WireMock (stubs de {@code /wiremock}).
 * {@code @ConnectWireMock} inyecta un cliente WireMock para verificar peticiones y registrar
 * stubs adicionales solo durante la prueba.
 */
@QuarkusTest
@ConnectWireMock
class AmadeusStyleAdapterTest {

    private static final LocalDate DEPARTURE = LocalDate.now().plusDays(30);

    WireMock wiremock;

    @Inject
    FlightOffersProvider provider;

    private final List<StubMapping> extraStubs = new ArrayList<>();

    @AfterEach
    void removeExtraStubs() {
        extraStubs.forEach(wiremock::removeStubMapping);
        extraStubs.clear();
    }

    @Test
    void the_configured_provider_is_the_amadeus_style_adapter() {
        // Los beans normal-scoped se inyectan a través de un client proxy de ArC (aquí dos: el del
        // productor y el del adaptador). Spring haría lo mismo con un proxy CGLIB.
        Object target = ClientProxy.unwrap(ClientProxy.unwrap(provider));
        assertThat(target).isInstanceOf(AmadeusStyleFlightOffersProvider.class);
    }

    @Test
    void maps_the_provider_contract_to_domain_offers() {
        Instant before = Instant.now();
        List<FlightOffer> offers = provider.search(criteria("BOG", "FCO", null, new PassengerCounts(2, 0, 1)));

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.offerId()).startsWith("AMS-");
            assertThat(offer.validatingCarrier()).isEqualTo(AirlineCode.of("AV"));
            assertThat(offer.operatingCarriers()).extracting(AirlineCode::value).containsExactly("AV", "IB");
            assertThat(offer.itineraries()).singleElement().satisfies(it -> {
                assertThat(it.direction()).isEqualTo(ItineraryDirection.OUTBOUND);
                assertThat(it.duration()).isEqualTo(Duration.parse("PT14H40M"));
                assertThat(it.segments()).extracting(OfferSegment::departure).containsExactly(
                        DEPARTURE.atTime(19, 5), DEPARTURE.plusDays(1).atTime(13, 15));
            });
            // 1120 × 2 asientos + 10 % por el infante; 100 millas por USD
            assertThat(offer.fare().total()).isEqualTo(Money.of("2352.00", "USD"));
            assertThat(offer.fare().milesEquivalent()).isEqualTo(Miles.of(235_200));
            assertThat(offer.passengers()).isEqualTo(new PassengerCounts(2, 0, 1));
            assertThat(offer.expiresAt()).isBetween(before.plus(Duration.ofMinutes(29)), Instant.now().plus(Duration.ofMinutes(31)));
        });
    }

    @Test
    void maps_round_trips_to_outbound_and_inbound_itineraries() {
        List<FlightOffer> offers = provider.search(criteria("LIM", "MIA", DEPARTURE.plusDays(7), new PassengerCounts(1, 0, 0)));

        assertThat(offers.getFirst().itineraries()).extracting(i -> i.direction())
                .containsExactly(ItineraryDirection.OUTBOUND, ItineraryDirection.INBOUND);
        assertThat(offers.getFirst().itineraries().get(1).segments().getFirst().departure().toLocalDate())
                .isEqualTo(DEPARTURE.plusDays(7));
    }

    @Test
    void sends_the_search_parameters_and_propagates_the_correlation_id() {
        wiremock.resetRequests();

        given().contentType(ContentType.JSON).header("X-Correlation-ID", "corr-amadeus-1")
                .body(Map.of("origin", "BOG", "destination", "MAD", "departureDate", DEPARTURE.toString(),
                        "passengers", Map.of("adults", 2, "children", 1, "infants", 1)))
                .post("/api/v1/offers/search")
                .then().statusCode(200);

        wiremock.verifyThat(1, getRequestedFor(urlPathEqualTo("/v2/shopping/flight-offers"))
                .withQueryParam("originLocationCode", equalTo("BOG"))
                .withQueryParam("destinationLocationCode", equalTo("MAD"))
                .withQueryParam("departureDate", equalTo(DEPARTURE.toString()))
                .withQueryParam("adults", equalTo("2"))
                .withQueryParam("children", equalTo("1"))
                .withQueryParam("infants", equalTo("1"))
                .withQueryParam("currencyCode", equalTo("USD"))
                .withHeader("Authorization", equalTo("Bearer wiremock-access-token"))
                .withHeader("X-Correlation-ID", equalTo("corr-amadeus-1")));
    }

    @Test
    void renews_the_token_once_when_the_provider_rejects_it() {
        provider.search(criteria("BOG", "MAD", null, new PassengerCounts(1, 0, 0))); // token en caché
        extraStubs.add(wiremock.register(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .withQueryParam("originLocationCode", equalTo("SCL"))
                .atPriority(1)
                .inScenario("token-revocado").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401).withBody("{\"errors\":[{\"status\":401}]}"))
                .willSetStateTo("renovado")));
        extraStubs.add(wiremock.register(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .withQueryParam("originLocationCode", equalTo("SCL"))
                .atPriority(1)
                .inScenario("token-revocado").whenScenarioStateIs("renovado")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"meta\":{\"count\":0},\"data\":[]}"))));
        wiremock.resetRequests();

        assertThat(provider.search(criteria("SCL", "MIA", null, new PassengerCounts(1, 0, 0)))).isEmpty();

        wiremock.verifyThat(1, postRequestedFor(urlEqualTo("/v1/security/oauth2/token")));
        wiremock.verifyThat(2, getRequestedFor(urlPathEqualTo("/v2/shopping/flight-offers")));
        wiremock.resetScenarios();
    }

    @Test
    void discards_offers_that_cannot_be_interpreted_instead_of_failing_the_search() {
        String validSegment = """
                {"departure":{"iataCode":"SCL","at":"%1$sT08:00:00"},"arrival":{"iataCode":"LIM","at":"%1$sT10:30:00"},
                 "carrierCode":"LA","number":"601","operating":{"carrierCode":"LA"}}""".formatted(DEPARTURE);
        String body = """
                {"meta":{"count":2},"data":[
                  {"id":"1","itineraries":[{"duration":"PT3H30M","segments":[%s]}],
                   "price":{"currency":"USD","grandTotal":"310.00"},"validatingAirlineCodes":["LA"]},
                  {"id":"2","itineraries":[{"duration":"PT3H30M","segments":[%s]}],
                   "price":{"currency":"USD","grandTotal":"310.00"},"validatingAirlineCodes":["LATAM"]}
                ]}""".formatted(validSegment, validSegment);
        extraStubs.add(wiremock.register(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .withQueryParam("originLocationCode", equalTo("SCL"))
                .withQueryParam("destinationLocationCode", equalTo("LIM"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body))));

        assertThat(provider.search(criteria("SCL", "LIM", null, new PassengerCounts(1, 0, 0))))
                .singleElement().extracting(FlightOffer::validatingCarrier).isEqualTo(AirlineCode.of("LA"));
    }

    private static OfferSearchCriteria criteria(String from, String to, LocalDate returnDate, PassengerCounts pax) {
        return new OfferSearchCriteria(AirportCode.of(from), AirportCode.of(to), DEPARTURE, returnDate, pax, null);
    }
}
