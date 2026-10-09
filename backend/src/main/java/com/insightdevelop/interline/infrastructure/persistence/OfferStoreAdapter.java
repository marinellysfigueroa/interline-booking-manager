package com.insightdevelop.interline.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.port.OfferStore;
import com.insightdevelop.interline.infrastructure.persistence.entity.FlightOfferEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Optional;

/**
 * Guarda las ofertas como JSON. Se serializan los records del dominio directamente: las
 * ofertas viven minutos, así que un cambio de forma del record solo invalida ofertas en
 * vuelo (que el cliente puede volver a buscar).
 */
@ApplicationScoped
public class OfferStoreAdapter implements OfferStore {

    private final FlightOfferPanacheRepository panache;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // Inyección por constructor: igual que en Spring, no hace falta @Inject con un único constructor.
    OfferStoreAdapter(FlightOfferPanacheRepository panache, ObjectMapper objectMapper, Clock clock) {
        this.panache = panache;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** {@code @Transactional} de Jakarta ≈ {@code @Transactional} de Spring (REQUIRED por defecto). */
    @Override
    @Transactional
    public void saveAll(Iterable<FlightOffer> offers) {
        for (FlightOffer offer : offers) {
            var e = new FlightOfferEntity();
            e.offerId = offer.offerId();
            e.payload = write(offer);
            e.expiresAt = offer.expiresAt();
            e.createdAt = clock.instant();
            panache.persist(e);
        }
    }

    @Override
    public Optional<FlightOffer> findById(String offerId) {
        return panache.findByIdOptional(offerId).map(e -> read(e.payload));
    }

    private String write(FlightOffer offer) {
        try {
            return objectMapper.writeValueAsString(offer);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FlightOffer read(String json) {
        try {
            return objectMapper.readValue(json, FlightOffer.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
