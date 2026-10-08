package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lista validada de pasajeros de una reserva. Además de las reglas de cantidad de
 * {@link PassengerCounts} verifica:
 * <ul>
 *   <li>que la edad de cada pasajero corresponda a su tipo a la fecha del primer vuelo;</li>
 *   <li>que cada INF esté asociado a un ADT que exista en la misma reserva;</li>
 *   <li>que ningún ADT lleve más de un INF.</li>
 * </ul>
 */
public final class PassengerManifest {

    private final List<Passenger> passengers;
    private final PassengerCounts counts;

    private PassengerManifest(List<Passenger> passengers) {
        this.passengers = List.copyOf(passengers);
        this.counts = new PassengerCounts(
                count(PassengerType.ADT), count(PassengerType.CHD), count(PassengerType.INF));
    }

    /** Crea y valida el manifiesto para un viaje cuyo primer vuelo sale en {@code firstDepartureDate}. */
    public static PassengerManifest of(List<Passenger> passengers, LocalDate firstDepartureDate) {
        Objects.requireNonNull(passengers, "passengers");
        Objects.requireNonNull(firstDepartureDate, "firstDepartureDate");
        requireUniqueIds(passengers);
        var manifest = new PassengerManifest(passengers); // valida las cantidades
        manifest.requireAgesMatchTypes(firstDepartureDate);
        manifest.requireInfantsAssignedToDistinctAdults();
        return manifest;
    }

    public List<Passenger> all() {
        return passengers;
    }

    public PassengerCounts counts() {
        return counts;
    }

    public int size() {
        return passengers.size();
    }

    private int count(PassengerType type) {
        return (int) passengers.stream().filter(p -> p.type() == type).count();
    }

    private static void requireUniqueIds(List<Passenger> passengers) {
        Set<PassengerId> ids = new HashSet<>();
        for (Passenger p : passengers) {
            if (!ids.add(p.id())) {
                throw new IllegalArgumentException("Pasajero duplicado: " + p.id().value());
            }
        }
    }

    private void requireAgesMatchTypes(LocalDate travelDate) {
        for (Passenger p : passengers) {
            if (!p.type().acceptsAge(p.dateOfBirth(), travelDate)) {
                throw new BusinessRuleViolationException(DomainErrorCode.PASSENGER_AGE_MISMATCH,
                        "%s (nacido el %s) no corresponde al tipo %s a la fecha de viaje %s"
                                .formatted(p.fullName(), p.dateOfBirth(), p.type(), travelDate));
            }
        }
    }

    private void requireInfantsAssignedToDistinctAdults() {
        Map<PassengerId, Passenger> byId = passengers.stream()
                .collect(Collectors.toMap(Passenger::id, Function.identity()));
        Map<PassengerId, PassengerId> infantByAdult = new HashMap<>();
        for (Passenger infant : passengers) {
            if (infant.type() != PassengerType.INF) {
                continue;
            }
            Passenger adult = byId.get(infant.associatedAdultId());
            if (adult == null || adult.type() != PassengerType.ADT) {
                throw new BusinessRuleViolationException(DomainErrorCode.INFANT_RULE_VIOLATION,
                        "El infante %s debe ir asociado a un adulto (ADT) de la reserva".formatted(infant.fullName()));
            }
            if (infantByAdult.putIfAbsent(adult.id(), infant.id()) != null) {
                throw new BusinessRuleViolationException(DomainErrorCode.INFANT_RULE_VIOLATION,
                        "El adulto %s ya lleva un infante; cada adulto puede llevar solo uno".formatted(adult.fullName()));
            }
        }
    }
}
