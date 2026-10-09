package com.insightdevelop.interline.infrastructure.provider.amadeus;

import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.OfferSearchCriteria;
import com.insightdevelop.interline.domain.port.FlightOffersProvider;
import com.insightdevelop.interline.domain.shared.ExternalServiceUnavailableException;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Adaptador del puerto {@link FlightOffersProvider} para un proveedor con contrato estilo
 * Amadeus Flight Offers Search (OAuth2 client credentials + GET de ofertas).
 *
 * <h2>Resiliencia (MicroProfile Fault Tolerance)</h2>
 * SmallRye aplica las políticas de fuera hacia dentro en este orden:
 * <pre>
 *   @Fallback( @Retry( @CircuitBreaker( @Timeout( llamada ) ) ) )
 * </pre>
 * <ul>
 *   <li>{@code @Timeout}: cada intento tiene un límite (3 s por defecto).</li>
 *   <li>{@code @CircuitBreaker}: si en una ventana de 6 llamadas falla el 50 %, el circuito se
 *       abre 10 s y las llamadas fallan al instante sin tocar al proveedor. Los 4xx
 *       ({@code Rejected}) no cuentan: son errores nuestros, no caídas del proveedor.</li>
 *   <li>{@code @Retry} + {@code @ExponentialBackoff}: 2 reintentos (200 ms, 400 ms… con
 *       jitter) solo ante fallos transitorios; no se reintenta un 4xx, un 401 persistente ni un
 *       circuito abierto.</li>
 *   <li>{@code @Fallback}: traduce cualquier fallo técnico al error de dominio
 *       {@link ExternalServiceUnavailableException} → HTTP 503 con {@code Retry-After}.</li>
 * </ul>
 * Los valores se pueden ajustar sin recompilar con
 * {@code quarkus.fault-tolerance."<clase>/search".*} (las pruebas usan valores cortos).
 *
 * <p>Spring Boot: equivale a Resilience4j ({@code @TimeLimiter}, {@code @Retry},
 * {@code @CircuitBreaker} con {@code fallbackMethod}).
 */
@ApplicationScoped
@Identifier("amadeus-style")
public class AmadeusStyleFlightOffersProvider implements FlightOffersProvider {

    static final String CIRCUIT_BREAKER = "flight-offers-provider";
    private static final Logger LOG = Logger.getLogger(AmadeusStyleFlightOffersProvider.class);

    private final AmadeusFlightOffersClient client;
    private final AmadeusTokenProvider tokens;
    private final AmadeusStyleConfig config;
    private final Clock clock;

    AmadeusStyleFlightOffersProvider(@RestClient AmadeusFlightOffersClient client, AmadeusTokenProvider tokens,
            AmadeusStyleConfig config, Clock clock) {
        this.client = client;
        this.tokens = tokens;
        this.config = config;
        this.clock = clock;
    }

    @Override
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 6, failureRatio = 0.5, delay = 10, delayUnit = ChronoUnit.SECONDS,
            successThreshold = 1,
            skipOn = {ProviderCallException.Rejected.class, ProviderCallException.Unauthorized.class})
    @CircuitBreakerName(CIRCUIT_BREAKER)
    @Retry(maxRetries = 2, delay = 200, jitter = 100, abortOn = {ProviderCallException.Rejected.class,
            ProviderCallException.Unauthorized.class, CircuitBreakerOpenException.class,
            IllegalStateException.class /* configuración incompleta: reintentar no sirve */})
    @ExponentialBackoff(factor = 2, maxDelay = 2000)
    @Fallback(fallbackMethod = "providerUnavailable")
    public List<FlightOffer> search(OfferSearchCriteria criteria) {
        LOG.debugf("Buscando ofertas %s-%s en el proveedor (hilo virtual: %s)", criteria.origin(),
                criteria.destination(), Thread.currentThread().isVirtual());
        AmadeusFlightOffersResponse response;
        String authorization = tokens.authorizationHeader();
        try {
            response = call(authorization, criteria);
        } catch (ProviderCallException.Unauthorized e) {
            // Token revocado o caducado antes de tiempo: se renueva una vez y se repite.
            tokens.invalidate(authorization);
            response = call(tokens.authorizationHeader(), criteria);
        }
        return AmadeusOfferMapper.toDomain(response, criteria, config, clock.instant());
    }

    private AmadeusFlightOffersResponse call(String authorization, OfferSearchCriteria criteria) {
        var pax = criteria.passengers();
        return client.search(authorization, criteria.origin().value(), criteria.destination().value(),
                criteria.departureDate().toString(), criteria.returnOn().map(Object::toString).orElse(null),
                pax.adults(), pax.children(), pax.infants(), config.currency(), config.maxResults());
    }

    /** Fallback: misma firma que {@link #search}; convierte el fallo técnico en error de dominio. */
    List<FlightOffer> providerUnavailable(OfferSearchCriteria criteria, Throwable cause) {
        LOG.warnf("Proveedor de vuelos no disponible para %s-%s: %s", criteria.origin(), criteria.destination(),
                cause.toString());
        throw new ExternalServiceUnavailableException(
                "El proveedor de vuelos no está disponible en este momento; inténtalo de nuevo en unos segundos",
                cause);
    }
}
