package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.domain.airline.Airline;
import com.insightdevelop.interline.domain.booking.TicketNumber;
import com.insightdevelop.interline.domain.booking.TicketNumberGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/** Números de ticket a partir de una secuencia de PostgreSQL (únicos aunque haya varias instancias). */
@ApplicationScoped
public class SequenceTicketNumberGenerator implements TicketNumberGenerator {

    private final EntityManager entityManager;

    SequenceTicketNumberGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public TicketNumber next(Airline validatingAirline) {
        long serial = ((Number) entityManager.createNativeQuery("select nextval('ticket_serial_seq')")
                .getSingleResult()).longValue();
        return TicketNumber.of(validatingAirline.accountingCode(), serial);
    }
}
