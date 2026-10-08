package com.insightdevelop.interline.domain.booking;

/**
 * Estado de un pago. {@code AUTHORIZED → CAPTURED} al emitir y {@code AUTHORIZED → RELEASED}
 * al compensar o cancelar. Pasar al mismo estado es un no-op.
 */
public enum PaymentStatus {
    /** Importe autorizado en la pasarela y/o millas bloqueadas (hold) en el programa. */
    AUTHORIZED,
    /** Importe cobrado y/o millas redimidas. */
    CAPTURED,
    /** Autorización anulada y/o hold de millas liberado. */
    RELEASED;

    public boolean canTransitionTo(PaymentStatus target) {
        return this == AUTHORIZED && (target == CAPTURED || target == RELEASED);
    }
}
