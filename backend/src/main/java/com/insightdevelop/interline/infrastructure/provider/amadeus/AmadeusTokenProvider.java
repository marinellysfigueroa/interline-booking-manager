package com.insightdevelop.interline.infrastructure.provider.amadeus;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Obtiene y cachea el token OAuth2 (client credentials) hasta poco antes de que caduque.
 *
 * <p>Se usa {@link ReentrantLock} y no {@code synchronized}: en Java 21 un hilo virtual que
 * bloquea dentro de un {@code synchronized} queda "anclado" (pinned) a su hilo portador
 * mientras espera la respuesta HTTP, y se pierde la ventaja de los hilos virtuales.
 */
@ApplicationScoped
public class AmadeusTokenProvider {

    private static final Logger LOG = Logger.getLogger(AmadeusTokenProvider.class);

    private record CachedToken(String value, Instant refreshAt) {
    }

    private final AmadeusAuthClient authClient;
    private final AmadeusStyleConfig config;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile CachedToken cached;

    AmadeusTokenProvider(@RestClient AmadeusAuthClient authClient, AmadeusStyleConfig config, Clock clock) {
        this.authClient = authClient;
        this.config = config;
        this.clock = clock;
    }

    /** Cabecera {@code Authorization} lista para usar. */
    public String authorizationHeader() {
        CachedToken token = cached;
        if (token == null || !clock.instant().isBefore(token.refreshAt())) {
            token = refresh(token);
        }
        return "Bearer " + token.value();
    }

    /** Descarta el token (p. ej. tras un 401) para que la siguiente llamada pida uno nuevo. */
    public void invalidate(String rejectedHeader) {
        lock.lock();
        try {
            if (cached != null && rejectedHeader.equals("Bearer " + cached.value())) {
                cached = null;
            }
        } finally {
            lock.unlock();
        }
    }

    private static IllegalStateException missing(String variable) {
        return new IllegalStateException("Falta la credencial del proveedor de vuelos: " + variable);
    }

    private CachedToken refresh(CachedToken stale) {
        lock.lock();
        try {
            // Otro hilo pudo renovarlo mientras esperábamos el lock.
            if (cached != null && cached != stale && clock.instant().isBefore(cached.refreshAt())) {
                return cached;
            }
            AmadeusTokenResponse response = authClient.token("client_credentials",
                    config.clientId().orElseThrow(() -> missing("AMADEUS_CLIENT_ID")),
                    config.clientSecret().orElseThrow(() -> missing("AMADEUS_CLIENT_SECRET")));
            Instant refreshAt = clock.instant().plusSeconds(response.expiresIn()).minus(config.tokenExpirySkew());
            cached = new CachedToken(response.accessToken(), refreshAt);
            LOG.debugf("Token del proveedor renovado; válido hasta %s", refreshAt);
            return cached;
        } finally {
            lock.unlock();
        }
    }
}
