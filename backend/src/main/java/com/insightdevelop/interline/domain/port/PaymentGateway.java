package com.insightdevelop.interline.domain.port;

import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.shared.Money;

/** Pasarela de pago en dinero. Operaciones idempotentes. */
public interface PaymentGateway {

    /**
     * @param idempotencyKey misma clave = misma autorización (no se duplica el cargo)
     * @return referencia de la autorización
     */
    String authorize(BookingLocator locator, Money amount, String idempotencyKey);

    void capture(String authorizationRef);

    /** Anula la autorización; anular una ya anulada no falla. */
    void release(String authorizationRef);
}
