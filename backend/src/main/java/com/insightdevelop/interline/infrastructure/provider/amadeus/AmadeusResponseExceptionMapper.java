package com.insightdevelop.interline.infrastructure.provider.amadeus;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Convierte respuestas de error HTTP del proveedor en excepciones clasificadas (en lugar de
 * la {@code WebApplicationException} genérica del REST Client).
 */
public class AmadeusResponseExceptionMapper implements ResponseExceptionMapper<ProviderCallException> {

    @Override
    public ProviderCallException toThrowable(Response response) {
        int status = response.getStatus();
        String message = "Proveedor de vuelos respondió HTTP " + status;
        if (status == 401) {
            return new ProviderCallException.Unauthorized(message);
        }
        if (status >= 500 || status == 429 || status == 408) {
            return new ProviderCallException.Transient(status, message);
        }
        return new ProviderCallException.Rejected(status, message);
    }

    @Override
    public boolean handles(int status, jakarta.ws.rs.core.MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }
}
