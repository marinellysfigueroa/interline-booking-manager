package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.booking.Contact;
import com.insightdevelop.interline.domain.booking.PassengerType;
import java.time.LocalDate;
import java.util.List;

/**
 * @param tenant aerolínea white-label desde la que se reserva; {@code null} si no aplica
 */
public record CreateBookingCommand(String offerId, Contact contact, List<PassengerDraft> passengers,
        AirlineCode tenant) {

    public CreateBookingCommand {
        passengers = List.copyOf(passengers);
    }

    /**
     * Pasajero tal como llega del cliente: los infantes referencian a su adulto con la
     * referencia local {@code ref}, porque aún no existen identificadores.
     */
    public record PassengerDraft(String ref, PassengerType type, String firstName, String lastName,
            LocalDate dateOfBirth, String associatedAdultRef) {
    }
}
