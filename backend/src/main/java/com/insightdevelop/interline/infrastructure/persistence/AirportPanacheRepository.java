package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.infrastructure.persistence.entity.AirportEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;


/** Repositorio Panache de {@link AirportEntity} (≈ un {@code JpaRepository} de Spring Data). */
@ApplicationScoped
public class AirportPanacheRepository implements PanacheRepositoryBase<AirportEntity, String> {
}
