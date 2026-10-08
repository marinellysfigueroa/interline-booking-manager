package com.insightdevelop.interline.infrastructure.provider.amadeus;

import com.insightdevelop.interline.infrastructure.rest.correlation.CorrelationIdClientFilter;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Búsqueda de ofertas (GET con parámetros, forma estilo Amadeus definida en los stubs).
 *
 * <p>Es un cliente <b>bloqueante</b>: se invoca desde un hilo virtual (ver
 * {@code OffersResource}). Con Mutiny se declararía {@code Uni<AmadeusFlightOffersResponse>}.
 */
@RegisterRestClient(configKey = "amadeus")
@RegisterProvider(CorrelationIdClientFilter.class)
@RegisterProvider(AmadeusResponseExceptionMapper.class)
@Path("/v2/shopping/flight-offers")
public interface AmadeusFlightOffersClient {

    @GET
    @Produces({"application/vnd.amadeus+json", "application/json"})
    AmadeusFlightOffersResponse search(
            @HeaderParam("Authorization") String authorization,
            @QueryParam("originLocationCode") String origin,
            @QueryParam("destinationLocationCode") String destination,
            @QueryParam("departureDate") String departureDate,
            @QueryParam("returnDate") String returnDate,
            @QueryParam("adults") int adults,
            @QueryParam("children") int children,
            @QueryParam("infants") int infants,
            @QueryParam("currencyCode") String currencyCode,
            @QueryParam("max") int max);
}
