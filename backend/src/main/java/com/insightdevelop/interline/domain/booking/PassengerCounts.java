package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;

/**
 * Composición de un grupo de pasajeros. Concentra las reglas de cantidad que comparten
 * la búsqueda de ofertas y la reserva:
 * <ul>
 *   <li>al menos un adulto (no se modelan menores no acompañados);</li>
 *   <li>como máximo {@value #MAX_SEATED_PASSENGERS} asientos (ADT + CHD), límite estándar de un PNR;</li>
 *   <li>nunca más infantes que adultos (cada infante viaja en brazos de un adulto distinto).</li>
 * </ul>
 */
public record PassengerCounts(int adults, int children, int infants) {

    public static final int MAX_SEATED_PASSENGERS = 9;

    public PassengerCounts {
        if (adults < 0 || children < 0 || infants < 0) {
            throw new IllegalArgumentException("Las cantidades de pasajeros no pueden ser negativas");
        }
        if (adults == 0) {
            throw new BusinessRuleViolationException(DomainErrorCode.NO_ADULT_PASSENGER,
                    "La reserva necesita al menos un adulto");
        }
        if (adults + children > MAX_SEATED_PASSENGERS) {
            throw new BusinessRuleViolationException(DomainErrorCode.TOO_MANY_PASSENGERS,
                    "Máximo %d pasajeros con asiento; se pidieron %d".formatted(MAX_SEATED_PASSENGERS, adults + children));
        }
        if (infants > adults) {
            throw new BusinessRuleViolationException(DomainErrorCode.INFANT_RULE_VIOLATION,
                    "Hay %d infantes y solo %d adultos; cada infante debe ir con un adulto distinto"
                            .formatted(infants, adults));
        }
    }

    public int seated() {
        return adults + children;
    }

    public int total() {
        return adults + children + infants;
    }
}
