package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.infrastructure.persistence.entity.IdempotencyRecordEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;


/** Repositorio Panache de {@link IdempotencyRecordEntity} (≈ un {@code JpaRepository} de Spring Data). */
@ApplicationScoped
public class IdempotencyRecordPanacheRepository implements PanacheRepositoryBase<IdempotencyRecordEntity, IdempotencyRecordEntity.Key> {
}
