package com.insightdevelop.interline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA del catálogo de aeropuertos.
 *
 * <p>Con el patrón Repository de Panache las entidades son JPA "normales" (no extienden
 * {@code PanacheEntity}); se usan campos públicos por brevedad, que Hibernate accede
 * directamente. Igual que una {@code @Entity} en Spring Data JPA.
 */
@Entity
@Table(name = "airport")
public class AirportEntity {

    @Id
    @Column(length = 3)
    public String code;

    public String name;

    public String city;

    @Column(name = "country_code")
    public String countryCode;

    @Column(name = "time_zone")
    public String timeZone;

    @Column(name = "search_key")
    public String searchKey;
}
