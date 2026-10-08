package com.insightdevelop.interline.infrastructure.provider.amadeus;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import com.insightdevelop.interline.infrastructure.rest.correlation.CorrelationIdClientFilter;

/**
 * Endpoint OAuth2 (client credentials) del proveedor.
 *
 * <p>MicroProfile REST Client: una interfaz anotada y Quarkus genera la implementación;
 * la URL sale de {@code quarkus.rest-client.amadeus-auth.url}. Spring Boot: equivale a una
 * interfaz {@code @HttpExchange} (o a un cliente OpenFeign).
 */
@RegisterRestClient(configKey = "amadeus-auth")
@RegisterProvider(CorrelationIdClientFilter.class)
@Path("/v1/security/oauth2/token")
public interface AmadeusAuthClient {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    AmadeusTokenResponse token(
            @FormParam("grant_type") String grantType,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret);
}
