package com.insightdevelop.interline.infrastructure.rest.correlation;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

/**
 * Propaga {@code X-Correlation-ID} a las llamadas salientes del REST Client, para poder
 * seguir una petición también en los logs del proveedor.
 *
 * <p>Spring Boot: un {@code ClientHttpRequestInterceptor} de {@code RestClient}.
 */
public class CorrelationIdClientFilter implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext request) {
        String correlationId = CorrelationIdFilter.current();
        if (correlationId != null) {
            request.getHeaders().putSingle(CorrelationIdFilter.HEADER, correlationId);
        }
    }
}
