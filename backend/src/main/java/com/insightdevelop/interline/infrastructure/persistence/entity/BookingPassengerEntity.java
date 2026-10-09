package com.insightdevelop.interline.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "booking_passenger")
public class BookingPassengerEntity {

    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    public BookingEntity booking;

    public int position;

    public String type;

    @Column(name = "first_name")
    public String firstName;

    @Column(name = "last_name")
    public String lastName;

    @Column(name = "date_of_birth")
    public LocalDate dateOfBirth;

    @Column(name = "associated_adult_id")
    public UUID associatedAdultId;
}
