package com.insightdevelop.interline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Oferta guardada como JSONB: se lee entera por {@code offerId} y caduca en minutos. */
@Entity
@Table(name = "flight_offer")
public class FlightOfferEntity {

    @Id
    @Column(name = "offer_id")
    public String offerId;

    @JdbcTypeCode(SqlTypes.JSON)
    public String payload;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "created_at")
    public Instant createdAt;
}
