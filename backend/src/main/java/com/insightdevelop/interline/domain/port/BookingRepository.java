package com.insightdevelop.interline.domain.port;

import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.shared.Page;
import com.insightdevelop.interline.domain.shared.PageRequest;
import java.util.Optional;
import java.util.Set;

/**
 * Repositorio del agregado Booking (patrón Repository de DDD).
 *
 * <p>Spring Boot: sería una interfaz {@code JpaRepository}; aquí la implementará un
 * {@code PanacheRepository} en infraestructura, que traduce entre el agregado y las
 * entidades JPA.
 */
public interface BookingRepository {

    Optional<Booking> findByLocator(BookingLocator locator);

    boolean existsByLocator(BookingLocator locator);

    /** Inserta o actualiza el agregado completo (con control de concurrencia optimista). */
    void save(Booking booking);

    /** Reservas más recientes primero. */
    Page<Booking> search(Query query, PageRequest pageRequest);

    /**
     * @param statuses          vacío = todos los estados
     * @param validatingCarrier {@code null} = todas las validadoras (sin filtro de tenant)
     */
    record Query(Set<BookingStatus> statuses, AirlineCode validatingCarrier) {

        public Query {
            statuses = Set.copyOf(statuses);
        }

        public static Query all() {
            return new Query(Set.of(), null);
        }
    }
}
