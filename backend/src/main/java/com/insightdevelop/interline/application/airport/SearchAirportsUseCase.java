package com.insightdevelop.interline.application.airport;

import com.insightdevelop.interline.domain.airport.Airport;
import com.insightdevelop.interline.domain.port.AirportRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class SearchAirportsUseCase {

    private final AirportRepository airports;

    SearchAirportsUseCase(AirportRepository airports) {
        this.airports = airports;
    }

    public List<Airport> search(String query, int limit) {
        return airports.search(query, limit);
    }
}
