package com.insightdevelop.interline.domain.booking;

import java.time.LocalDate;
import java.time.Period;

/**
 * Tipo de pasajero según su edad <b>a la fecha del primer vuelo</b> (criterio habitual
 * de las aerolíneas: un niño que cumple 12 durante el viaje sigue siendo CHD).
 */
public enum PassengerType {
    /** Adulto: 12 años o más. */
    ADT(12, Integer.MAX_VALUE),
    /** Niño: de 2 a 11 años, ocupa asiento. */
    CHD(2, 11),
    /** Infante: menor de 2 años, viaja en brazos de un adulto y no ocupa asiento. */
    INF(0, 1);

    private final int minAge;
    private final int maxAge;

    PassengerType(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public boolean occupiesSeat() {
        return this != INF;
    }

    public boolean acceptsAge(LocalDate dateOfBirth, LocalDate travelDate) {
        if (dateOfBirth.isAfter(travelDate)) {
            return false;
        }
        int age = Period.between(dateOfBirth, travelDate).getYears();
        return age >= minAge && age <= maxAge;
    }
}
