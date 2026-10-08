package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tarifa total de la reserva, en dinero y su equivalente en millas.
 *
 * <p>Un pago mixto cubre la tarifa si la suma de las fracciones cubiertas es ≥ 1:
 * {@code cash/total + miles/milesEquivalent ≥ 1}. Se evalúa con multiplicación
 * cruzada para no introducir errores de redondeo.
 */
public record Fare(Money total, Miles milesEquivalent) {

    public Fare {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(milesEquivalent, "milesEquivalent");
        if (!total.isPositive() || !milesEquivalent.isPositive()) {
            throw new IllegalArgumentException("La tarifa debe ser positiva en dinero y en millas");
        }
    }

    /**
     * @param cash  parte en dinero (puede ser {@code null} si se paga solo con millas)
     * @param miles parte en millas
     */
    public boolean isCoveredBy(Money cash, Miles miles) {
        Objects.requireNonNull(miles, "miles");
        BigDecimal cashAmount = BigDecimal.ZERO;
        if (cash != null) {
            total.requireSameCurrency(cash);
            cashAmount = cash.amount();
        }
        BigDecimal milesEq = BigDecimal.valueOf(milesEquivalent.value());
        BigDecimal covered = cashAmount.multiply(milesEq)
                .add(BigDecimal.valueOf(miles.value()).multiply(total.amount()));
        return covered.compareTo(total.amount().multiply(milesEq)) >= 0;
    }
}
