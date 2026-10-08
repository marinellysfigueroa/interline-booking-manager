package com.insightdevelop.interline.domain.offer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Trayecto de ida o de vuelta, con sus conexiones.
 *
 * @param duration duración total calculada por el proveedor (incluye conexiones y
 *                 diferencias horarias, que no se pueden deducir de las horas locales)
 */
public record Itinerary(ItineraryDirection direction, Duration duration, List<OfferSegment> segments) {

    public Itinerary {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(duration, "duration");
        segments = List.copyOf(segments);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Un itinerario necesita al menos un segmento");
        }
        for (int i = 1; i < segments.size(); i++) {
            if (!segments.get(i).origin().equals(segments.get(i - 1).destination())) {
                throw new IllegalArgumentException("Itinerario no contiguo: %s no conecta con %s"
                        .formatted(segments.get(i - 1).destination(), segments.get(i).origin()));
            }
        }
    }
}
