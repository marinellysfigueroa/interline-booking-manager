package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.port.BookingRepository;
import com.insightdevelop.interline.domain.shared.Page;
import com.insightdevelop.interline.domain.shared.PageRequest;
import com.insightdevelop.interline.infrastructure.persistence.entity.BookingEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia del agregado Booking: implementa el puerto de dominio
 * {@link BookingRepository} usando un {@link BookingPanacheRepository}.
 *
 * <p>El adaptador <b>compone</b> el repositorio Panache en lugar de heredarlo, para que el
 * puerto de dominio no quede mezclado con la API de Panache ({@code findById},
 * {@code persist}...). Panache no genera consultas por nombre de método como Spring
 * Data; se escriben en HQL abreviado, p. ej. {@code find("locator", x)}.
 *
 * <p>{@code @ApplicationScoped} ≈ singleton de Spring ({@code @Repository}/{@code @Service}),
 * aunque inyectado mediante un proxy cliente y creado de forma perezosa.
 *
 * <p>Concurrencia: los casos de uso cargan y guardan dentro de la misma transacción, de
 * modo que la entidad sigue gestionada y {@code @Version} detecta escrituras concurrentes
 * al hacer flush ({@code OptimisticLockException} → 409).
 */
@ApplicationScoped
public class BookingRepositoryAdapter implements BookingRepository {

    private final BookingPanacheRepository panache;

    BookingRepositoryAdapter(BookingPanacheRepository panache) {
        this.panache = panache;
    }

    @Override
    public Optional<Booking> findByLocator(BookingLocator locator) {
        return findEntity(locator).map(BookingEntityMapper::toDomain);
    }

    @Override
    public boolean existsByLocator(BookingLocator locator) {
        return panache.count("locator", locator.value()) > 0;
    }

    @Override
    public Optional<BookingStatus> findStatus(BookingLocator locator) {
        return panache.find("select status from BookingEntity where locator = ?1", locator.value())
                .project(String.class).firstResultOptional().map(BookingStatus::valueOf);
    }

    @Override
    public void save(Booking booking) {
        findEntity(booking.locator()).ifPresentOrElse(
                entity -> BookingEntityMapper.update(entity, booking),
                () -> panache.persist(BookingEntityMapper.newEntity(booking)));
    }

    @Override
    public Page<Booking> search(Query query, PageRequest pageRequest) {
        List<String> where = new ArrayList<>();
        Parameters params = new Parameters();
        if (!query.statuses().isEmpty()) {
            where.add("status in :statuses");
            params.and("statuses", query.statuses().stream().map(Enum::name).toList());
        }
        if (query.validatingCarrier() != null) {
            where.add("validatingCarrier = :carrier");
            params.and("carrier", query.validatingCarrier().value());
        }
        Sort sort = Sort.descending("createdAt").and("locator");
        PanacheQuery<BookingEntity> q = where.isEmpty()
                ? panache.findAll(sort)
                : panache.find(String.join(" and ", where), sort, params);
        long total = q.count();
        List<Booking> items = q.page(io.quarkus.panache.common.Page.of(pageRequest.page(), pageRequest.size()))
                .list().stream().map(BookingEntityMapper::toDomain).toList();
        return new Page<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    private Optional<BookingEntity> findEntity(BookingLocator locator) {
        return panache.find("locator", locator.value()).firstResultOptional();
    }
}
