package com.insightdevelop.interline.infrastructure.gateway.simulated;

import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.port.PaymentGateway;
import com.insightdevelop.interline.domain.shared.Money;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pasarela de pago simulada. La referencia de autorización se deriva de la clave de
 * idempotencia, así que autorizar dos veces con la misma clave devuelve la misma
 * autorización (no hay doble cargo).
 */
@ApplicationScoped
public class SimulatedPaymentGateway implements PaymentGateway {

    enum State { AUTHORIZED, CAPTURED, RELEASED }

    private final Map<String, State> authorizations = new ConcurrentHashMap<>();

    @Override
    public String authorize(BookingLocator locator, Money amount, String idempotencyKey) {
        String ref = "AUTH-" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 12).toUpperCase();
        authorizations.putIfAbsent(ref, State.AUTHORIZED);
        return ref;
    }

    @Override
    public void capture(String authorizationRef) {
        authorizations.compute(authorizationRef, (ref, state) -> {
            if (state == State.RELEASED) {
                throw new IllegalStateException("La autorización " + ref + " ya fue anulada");
            }
            return State.CAPTURED;
        });
    }

    @Override
    public void release(String authorizationRef) {
        authorizations.compute(authorizationRef, (ref, state) -> {
            if (state == State.CAPTURED) {
                throw new IllegalStateException("La autorización " + ref + " ya fue capturada");
            }
            return State.RELEASED;
        });
    }

    State stateOf(String ref) {
        return authorizations.get(ref);
    }
}
