package com.insightdevelop.interline.infrastructure.provider.amadeus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThan;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Políticas de Fault Tolerance del adaptador, provocando fallos con stubs de WireMock sobre
 * rutas desde SCL (que no tienen stubs propios). En el perfil test: timeout 800 ms, 2
 * reintentos de 20 ms y circuit breaker con ventana de 4 llamadas.
 */
@QuarkusTest
@ConnectWireMock
class FlightProviderResilienceTest {

    private static final String OFFERS = "/v2/shopping/flight-offers";
    private static final String EMPTY = "{\"meta\":{\"count\":0},\"data\":[]}";

    WireMock wiremock;

    private final List<StubMapping> extraStubs = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanState() {
        // El circuito es compartido por toda la app: se cierra antes y después para no afectar a otras pruebas.
        CircuitBreakerMaintenance.get().reset(AmadeusStyleFlightOffersProvider.CIRCUIT_BREAKER);
        if (wiremock != null) {
            extraStubs.forEach(wiremock::removeStubMapping);
            wiremock.resetScenarios();
        }
        extraStubs.clear();
    }

    @Test
    void retries_transient_failures_and_succeeds() {
        stubScenario("LIM", aResponse().withStatus(503), "fallo-1");
        stubScenario("LIM", "fallo-1", aResponse().withStatus(500), "fallo-2");
        stubScenario("LIM", "fallo-2", ok(), "ok");
        wiremock.resetRequests();

        search("LIM").statusCode(200).body("count", is(0));

        wiremock.verifyThat(3, offersFrom("LIM"));
    }

    @Test
    void timeout_after_retries_becomes_a_503_problem_with_retry_after() {
        stub("BOG", aResponse().withStatus(200).withFixedDelay(1500).withBody(EMPTY));
        wiremock.resetRequests();

        search("BOG").statusCode(503)
                .contentType("application/problem+json")
                .header("Retry-After", "5")
                .body("code", is("PROVIDER_UNAVAILABLE"));

        wiremock.verifyThat(3, offersFrom("BOG")); // 1 intento + 2 reintentos
    }

    @Test
    void client_errors_are_not_retried_and_do_not_open_the_circuit() {
        stub("MAD", aResponse().withStatus(400).withBody("{\"errors\":[{\"status\":400,\"code\":477}]}"));
        wiremock.resetRequests();

        for (int i = 0; i < 5; i++) {
            search("MAD").statusCode(503);
        }

        wiremock.verifyThat(5, offersFrom("MAD"));
        assertThat(state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void circuit_opens_after_repeated_failures_and_stops_calling_the_provider() {
        stub("FCO", aResponse().withStatus(500));
        wiremock.resetRequests();

        search("FCO").statusCode(503); // 3 intentos fallidos
        search("FCO").statusCode(503); // el 4.º fallo abre el circuito
        assertThat(state()).isEqualTo(CircuitBreakerState.OPEN);
        int callsWhenOpened = wiremock.find(offersFrom("FCO")).size();

        search("FCO").statusCode(503).body("detail", org.hamcrest.Matchers.containsString("no está disponible"));

        assertThat(wiremock.find(offersFrom("FCO"))).hasSize(callsWhenOpened);
        assertThat(callsWhenOpened).isEqualTo(4);
        // Con el circuito abierto, el resto de búsquedas también fallan rápido (incluso rutas sanas)
        search("MIA").statusCode(503);
    }

    @Test
    void an_open_circuit_recovers_after_reset() {
        stub("FCO", aResponse().withStatus(500));
        search("FCO");
        search("FCO");
        assertThat(state()).isEqualTo(CircuitBreakerState.OPEN);

        CircuitBreakerMaintenance.get().reset(AmadeusStyleFlightOffersProvider.CIRCUIT_BREAKER);

        given().contentType(ContentType.JSON)
                .body(Map.of("origin", "BOG", "destination", "MAD", "departureDate",
                        LocalDate.now().plusDays(30).toString(), "passengers", Map.of("adults", 1)))
                .post("/api/v1/offers/search")
                .then().statusCode(200).body("count", greaterThan(0));
    }

    private static ValidatableResponse search(String destination) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("origin", "SCL", "destination", destination, "departureDate",
                        LocalDate.now().plusDays(30).toString(), "passengers", Map.of("adults", 1)))
                .post("/api/v1/offers/search")
                .then();
    }

    private void stub(String destination, com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
        extraStubs.add(wiremock.register(get(urlPathEqualTo(OFFERS))
                .withQueryParam("originLocationCode", equalTo("SCL"))
                .withQueryParam("destinationLocationCode", equalTo(destination))
                .atPriority(1)
                .willReturn(response)));
    }

    private void stubScenario(String destination, com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response,
            String nextState) {
        stubScenario(destination, Scenario.STARTED, response, nextState);
    }

    private void stubScenario(String destination, String state,
            com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response, String nextState) {
        extraStubs.add(wiremock.register(get(urlPathEqualTo(OFFERS))
                .withQueryParam("originLocationCode", equalTo("SCL"))
                .withQueryParam("destinationLocationCode", equalTo(destination))
                .atPriority(1)
                .inScenario("reintentos-" + destination).whenScenarioStateIs(state)
                .willReturn(response)
                .willSetStateTo(nextState)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder ok() {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(EMPTY);
    }

    private static RequestPatternBuilder offersFrom(String destination) {
        return getRequestedFor(urlPathEqualTo(OFFERS))
                .withQueryParam("originLocationCode", equalTo("SCL"))
                .withQueryParam("destinationLocationCode", equalTo(destination));
    }

    private static CircuitBreakerState state() {
        return CircuitBreakerMaintenance.get().currentState(AmadeusStyleFlightOffersProvider.CIRCUIT_BREAKER);
    }
}
