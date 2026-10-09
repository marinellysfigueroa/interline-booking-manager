package com.insightdevelop.interline.domain.port;

import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.OfferSearchCriteria;
import com.insightdevelop.interline.domain.shared.ExternalServiceUnavailableException;
import java.util.List;

/**
 * Proveedor externo de ofertas de vuelo. Cada proveedor (el adaptador estilo Amadeus de
 * la fase 3, o uno real en el futuro) es una implementación de este puerto, elegida por
 * configuración.
 */
public interface FlightOffersProvider {

    /**
     * @return ofertas normalizadas (puede ser vacía)
     * @throws ExternalServiceUnavailableException si el proveedor no responde tras los reintentos
     */
    List<FlightOffer> search(OfferSearchCriteria criteria);
}
