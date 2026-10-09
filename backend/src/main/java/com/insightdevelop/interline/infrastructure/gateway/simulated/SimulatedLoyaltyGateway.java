package com.insightdevelop.interline.infrastructure.gateway.simulated;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.port.LoyaltyGateway;
import com.insightdevelop.interline.domain.shared.Miles;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Programa de lealtad simulado: holds de millas idempotentes por clave. */
@ApplicationScoped
public class SimulatedLoyaltyGateway implements LoyaltyGateway {

    enum State { HELD, REDEEMED, RELEASED }

    private final Map<String, State> holds = new ConcurrentHashMap<>();

    @Override
    public String holdMiles(AirlineCode program, String memberNumber, Miles miles, String idempotencyKey) {
        String ref = "HOLD-" + program + "-" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 10).toUpperCase();
        holds.putIfAbsent(ref, State.HELD);
        return ref;
    }

    @Override
    public void redeem(String holdRef) {
        holds.compute(holdRef, (ref, state) -> {
            if (state == State.RELEASED) {
                throw new IllegalStateException("El hold " + ref + " ya fue liberado");
            }
            return State.REDEEMED;
        });
    }

    @Override
    public void releaseHold(String holdRef) {
        holds.compute(holdRef, (ref, state) -> {
            if (state == State.REDEEMED) {
                throw new IllegalStateException("El hold " + ref + " ya fue redimido");
            }
            return State.RELEASED;
        });
    }
}
