package com.insightdevelop.interline.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Raíz persistente de la reserva. No es el agregado de dominio: el adaptador
 * {@code BookingRepositoryAdapter} traduce entre ambos.
 */
@Entity
@Table(name = "booking")
public class BookingEntity {

    @Id
    public UUID id;

    public String locator;

    @Column(name = "validating_carrier")
    public String validatingCarrier;

    @Column(name = "offer_id")
    public String offerId;

    public String status;

    @Column(name = "contact_email")
    public String contactEmail;

    @Column(name = "contact_phone")
    public String contactPhone;

    @Column(name = "fare_amount")
    public BigDecimal fareAmount;

    @Column(name = "fare_currency")
    public String fareCurrency;

    @Column(name = "fare_miles")
    public Long fareMiles;

    public String origin;

    public String destination;

    @Column(name = "first_departure")
    public LocalDateTime firstDeparture;

    @Column(name = "passenger_count")
    public int passengerCount;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    /** Bloqueo optimista: igual que {@code @Version} en Spring Data JPA. */
    @Version
    public long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    public List<BookingPassengerEntity> passengers = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    public List<BookingSegmentEntity> segments = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt")
    public List<BookingPaymentEntity> payments = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number")
    public List<BookingTicketEntity> tickets = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    public List<BookingStatusChangeEntity> statusHistory = new ArrayList<>();
}
