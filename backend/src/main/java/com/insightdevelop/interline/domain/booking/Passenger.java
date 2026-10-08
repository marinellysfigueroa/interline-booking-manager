package com.insightdevelop.interline.domain.booking;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Pasajero de la reserva (inmutable).
 *
 * @param associatedAdultId solo para {@code INF}: el adulto que lo lleva en brazos
 */
public record Passenger(
        PassengerId id,
        PassengerType type,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        PassengerId associatedAdultId) {

    public Passenger {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        if (firstName.isBlank() || lastName.isBlank()) {
            throw new IllegalArgumentException("Nombre y apellido son obligatorios");
        }
        if (type == PassengerType.INF && associatedAdultId == null) {
            throw new IllegalArgumentException("Un infante debe estar asociado a un adulto");
        }
        if (type != PassengerType.INF && associatedAdultId != null) {
            throw new IllegalArgumentException("Solo un infante puede asociarse a un adulto");
        }
    }

    public static Passenger adult(String firstName, String lastName, LocalDate dateOfBirth) {
        return new Passenger(PassengerId.random(), PassengerType.ADT, firstName, lastName, dateOfBirth, null);
    }

    public static Passenger child(String firstName, String lastName, LocalDate dateOfBirth) {
        return new Passenger(PassengerId.random(), PassengerType.CHD, firstName, lastName, dateOfBirth, null);
    }

    public static Passenger infant(String firstName, String lastName, LocalDate dateOfBirth, PassengerId adultId) {
        return new Passenger(PassengerId.random(), PassengerType.INF, firstName, lastName, dateOfBirth, adultId);
    }

    public Optional<PassengerId> associatedAdult() {
        return Optional.ofNullable(associatedAdultId);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
