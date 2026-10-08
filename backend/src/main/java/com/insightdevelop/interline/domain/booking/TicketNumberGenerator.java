package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.airline.Airline;

/**
 * Genera números de ticket únicos para una validadora. Es un puerto de salida que el
 * agregado necesita para emitir; vive junto a él para evitar un ciclo de paquetes con
 * {@code domain.port}. La implementación (fase 2) usará una secuencia de PostgreSQL.
 */
@FunctionalInterface
public interface TicketNumberGenerator {

    TicketNumber next(Airline validatingAirline);
}
