package com.insightdevelop.interline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_payment")
public class BookingPaymentEntity {

    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    public BookingEntity booking;

    public String method;

    @Column(name = "cash_amount")
    public BigDecimal cashAmount;

    @Column(name = "cash_currency")
    public String cashCurrency;

    public long miles;

    @Column(name = "member_number")
    public String memberNumber;

    @Column(name = "cash_authorization_ref")
    public String cashAuthorizationRef;

    @Column(name = "miles_hold_ref")
    public String milesHoldRef;

    public String status;

    @Column(name = "created_at")
    public Instant createdAt;
}
