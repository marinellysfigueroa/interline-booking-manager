package com.insightdevelop.interline.domain.port;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.shared.Miles;

/** Programa de lealtad de la aerolínea validadora. Operaciones idempotentes. */
public interface LoyaltyGateway {

    /**
     * Bloquea millas del socio sin redimirlas todavía.
     *
     * @return referencia del hold
     */
    String holdMiles(AirlineCode program, String memberNumber, Miles miles, String idempotencyKey);

    /** Redime las millas bloqueadas (equivale a capturar). */
    void redeem(String holdRef);

    /** Libera el hold; liberar uno ya liberado no falla. */
    void releaseHold(String holdRef);
}
