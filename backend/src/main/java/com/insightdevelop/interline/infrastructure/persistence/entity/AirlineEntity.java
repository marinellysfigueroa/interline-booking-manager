package com.insightdevelop.interline.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "airline")
public class AirlineEntity {

    @Id
    public String code;

    public String name;

    @Column(name = "accounting_code")
    public String accountingCode;

    @Column(name = "loyalty_program")
    public String loyaltyProgram;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "airline_interline_partner", joinColumns = @JoinColumn(name = "airline_code"))
    @Column(name = "partner_code")
    public Set<String> interlinePartners = new HashSet<>();
}
