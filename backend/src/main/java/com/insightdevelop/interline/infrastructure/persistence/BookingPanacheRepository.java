package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.infrastructure.persistence.entity.BookingEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/** Repositorio Panache de {@link BookingEntity} (≈ un {@code JpaRepository} de Spring Data). */
@ApplicationScoped
public class BookingPanacheRepository implements PanacheRepositoryBase<BookingEntity, UUID> {
}
