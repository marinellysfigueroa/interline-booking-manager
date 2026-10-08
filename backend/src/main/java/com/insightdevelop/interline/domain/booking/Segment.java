package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.shared.InvalidStateTransitionException;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tramo de vuelo de la reserva (entidad interna del agregado).
 *
 * <p>{@code departure} y {@code arrival} son horas <b>locales</b> de cada aeropuerto,
 * como en los sistemas de reservas. Por eso no se valida que la llegada sea posterior
 * a la salida: sin la zona horaria de cada aeropuerto esa comparación no es fiable.
 */
public final class Segment {

    private final SegmentId id;
    private final AirlineCode operatingCarrier;
    private final FlightNumber flightNumber;
    private final AirportCode origin;
    private final AirportCode destination;
    private final LocalDateTime departure;
    private final LocalDateTime arrival;
    private SegmentStatus status;

    private Segment(SegmentId id, AirlineCode operatingCarrier, FlightNumber flightNumber, AirportCode origin,
            AirportCode destination, LocalDateTime departure, LocalDateTime arrival, SegmentStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.operatingCarrier = Objects.requireNonNull(operatingCarrier, "operatingCarrier");
        this.flightNumber = Objects.requireNonNull(flightNumber, "flightNumber");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.departure = Objects.requireNonNull(departure, "departure");
        this.arrival = Objects.requireNonNull(arrival, "arrival");
        this.status = Objects.requireNonNull(status, "status");
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("Origen y destino no pueden ser iguales: " + origin);
        }
    }

    /** Segmento recién solicitado: nace en {@code UC} hasta que la operadora lo confirme. */
    public static Segment requested(AirlineCode operatingCarrier, FlightNumber flightNumber, AirportCode origin,
            AirportCode destination, LocalDateTime departure, LocalDateTime arrival) {
        return new Segment(SegmentId.random(), operatingCarrier, flightNumber, origin, destination,
                departure, arrival, SegmentStatus.UC);
    }

    /** Reconstrucción desde persistencia (no aplica reglas de creación). */
    public static Segment restore(SegmentId id, AirlineCode operatingCarrier, FlightNumber flightNumber,
            AirportCode origin, AirportCode destination, LocalDateTime departure, LocalDateTime arrival,
            SegmentStatus status) {
        return new Segment(id, operatingCarrier, flightNumber, origin, destination, departure, arrival, status);
    }

    /**
     * Aplica el estado informado por la operadora. Idempotente.
     *
     * @return {@code true} si el estado cambió
     */
    boolean applyStatus(SegmentStatus target) {
        if (status == target) {
            return false;
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Segmento " + label(), status, target);
        }
        status = target;
        return true;
    }

    public boolean isActive() {
        return status != SegmentStatus.XX;
    }

    public boolean isConfirmed() {
        return status == SegmentStatus.HK;
    }

    /** Etiqueta legible, p. ej. {@code IB3234 MAD-FCO}. */
    public String label() {
        return "%s%s %s-%s".formatted(operatingCarrier, flightNumber, origin, destination);
    }

    public SegmentId id() {
        return id;
    }

    public AirlineCode operatingCarrier() {
        return operatingCarrier;
    }

    public FlightNumber flightNumber() {
        return flightNumber;
    }

    public AirportCode origin() {
        return origin;
    }

    public AirportCode destination() {
        return destination;
    }

    public LocalDateTime departure() {
        return departure;
    }

    public LocalDateTime arrival() {
        return arrival;
    }

    public SegmentStatus status() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Segment other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return label() + " " + status;
    }
}
