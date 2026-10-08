package com.insightdevelop.interline.infrastructure.rest;

import com.insightdevelop.interline.application.airport.SearchAirportsUseCase;
import com.insightdevelop.interline.infrastructure.rest.api.AirportsApi;
import jakarta.ws.rs.core.Response;

/**
 * Adaptador REST de aeropuertos. Implementa la interfaz generada desde el contrato, que ya
 * trae las anotaciones JAX-RS y de Bean Validation.
 *
 * <p>Spring Boot: equivale a un {@code @RestController}. En Quarkus REST no hace falta
 * anotar la clase: basta con implementar una interfaz con {@code @Path}. Los recursos
 * son {@code @Singleton} por defecto (no {@code @RequestScoped}).
 */
public class AirportsResource implements AirportsApi {

    private final SearchAirportsUseCase searchAirports;

    AirportsResource(SearchAirportsUseCase searchAirports) {
        this.searchAirports = searchAirports;
    }

    @Override
    public Response searchAirports(String query, String xCorrelationID, String xTenant, Integer limit) {
        return Response.ok(searchAirports.search(query, limit).stream().map(RestMapper::toDto).toList()).build();
    }
}
