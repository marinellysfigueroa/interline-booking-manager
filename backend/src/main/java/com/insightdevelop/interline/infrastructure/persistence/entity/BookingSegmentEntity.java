package com.insightdevelop.interline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_segment")
public class BookingSegmentEntity {

    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    public BookingEntity booking;

    public int position;

    @Column(name = "operating_carrier")
    public String operatingCarrier;

    @Column(name = "flight_number")
    public String flightNumber;

    public String origin;

    public String destination;

    @Column(name = "departure_local")
    public LocalDateTime departureLocal;

    @Column(name = "arrival_local")
    public LocalDateTime arrivalLocal;

    public String status;
}
