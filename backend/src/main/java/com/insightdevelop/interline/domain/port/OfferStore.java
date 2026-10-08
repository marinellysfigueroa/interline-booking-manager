package com.insightdevelop.interline.domain.port;

import com.insightdevelop.interline.domain.offer.FlightOffer;
import java.util.Optional;

/**
 * Guarda las ofertas devueltas en una búsqueda para poder reservar después por
 * {@code offerId} sin confiar en precios enviados por el cliente.
 */
public interface OfferStore {

    void saveAll(Iterable<FlightOffer> offers);

    Optional<FlightOffer> findById(String offerId);
}
