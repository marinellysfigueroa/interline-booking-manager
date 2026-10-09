package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.infrastructure.persistence.entity.FlightOfferEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;


/** Repositorio Panache de {@link FlightOfferEntity} (≈ un {@code JpaRepository} de Spring Data). */
@ApplicationScoped
public class FlightOfferPanacheRepository implements PanacheRepositoryBase<FlightOfferEntity, String> {
}
