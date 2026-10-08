package com.insightdevelop.interline.infrastructure.rest;

import com.insightdevelop.interline.application.offer.SearchOffersUseCase;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.infrastructure.rest.api.OffersApi;
import com.insightdevelop.interline.infrastructure.rest.dto.OfferSearchRequestDto;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.core.Response;

/**
 * Búsqueda de ofertas.
 *
 * <h2>Hilos virtuales vs. Mutiny</h2>
 * {@code @RunOnVirtualThread} ejecuta el método en un hilo virtual de Java 21: el código sigue
 * siendo imperativo y bloqueante (REST Client síncrono, JDBC), pero mientras espera la red el
 * hilo portador queda libre, así que miles de búsquedas concurrentes no agotan el pool de
 * workers. Es la opción por defecto aquí porque la lógica es secuencial (token → búsqueda →
 * guardar ofertas → evaluar interline) y se lee igual que en Spring MVC.
 *
 * <p>Convendría <b>Mutiny</b> ({@code Uni}/{@code Multi} en el event loop) cuando hay que
 * <i>componer</i> I/O: consultar varios proveedores en paralelo y quedarse con el primero o
 * combinar resultados, aplicar backpressure o hacer streaming (SSE) de ofertas a medida que
 * llegan, o cuando toda la cadena ya es reactiva (Hibernate Reactive, Reactive PG client). Un
 * hilo virtual tampoco ayuda si el código hace mucha CPU o usa librerías que lo anclan
 * ({@code synchronized} alrededor de I/O).
 *
 * <p>Spring Boot: equivale a {@code spring.threads.virtual.enabled=true} (MVC sobre hilos
 * virtuales) frente a WebFlux.
 */
public class OffersResource implements OffersApi {

    private final SearchOffersUseCase searchOffers;

    OffersResource(SearchOffersUseCase searchOffers) {
        this.searchOffers = searchOffers;
    }

    @Override
    @RunOnVirtualThread
    public Response searchOffers(OfferSearchRequestDto request, String xCorrelationID, String xTenant) {
        AirlineCode tenant = xTenant == null ? null : AirlineCode.of(xTenant);
        return Response.ok(RestMapper.toDto(searchOffers.search(RestMapper.toCriteria(request, tenant)))).build();
    }
}
