package com.insightdevelop.interline.infrastructure.persistence;

import com.insightdevelop.interline.domain.airline.Airline;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airline.LoyaltyProgram;
import com.insightdevelop.interline.domain.port.AirlineRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class AirlineRepositoryAdapter implements AirlineRepository {

    private final AirlinePanacheRepository panache;

    AirlineRepositoryAdapter(AirlinePanacheRepository panache) {
        this.panache = panache;
    }

    @Override
    public Optional<Airline> findByCode(AirlineCode code) {
        return panache.findByIdOptional(code.value()).map(e -> new Airline(
                new AirlineCode(e.code),
                e.name,
                e.accountingCode,
                Optional.ofNullable(e.loyaltyProgram).map(LoyaltyProgram::new),
                e.interlinePartners.stream().map(AirlineCode::new).collect(Collectors.toSet())));
    }
}
