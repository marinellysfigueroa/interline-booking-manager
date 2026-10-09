package com.insightdevelop.interline.domain.booking;

/** Medio de pago: efectivo (tarjeta/caja), millas del programa de la validadora o mixto. */
public enum PaymentMethod {
    CASH,
    MILES,
    MIXED;

    public boolean usesCash() {
        return this != MILES;
    }

    public boolean usesMiles() {
        return this != CASH;
    }
}
