package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.infrastructure.persistence.entity.AirlineEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;


/** Repositorio Panache de {@link AirlineEntity} (≈ un {@code JpaRepository} de Spring Data). */
@ApplicationScoped
public class AirlinePanacheRepository implements PanacheRepositoryBase<AirlineEntity, String> {
}
