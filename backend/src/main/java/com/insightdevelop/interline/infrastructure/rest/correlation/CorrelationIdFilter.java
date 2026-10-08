package com.insightdevelop.interline.infrastructure.rest.correlation;

import io.opentelemetry.api.trace.Span;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/**
 * Propaga el identificador de correlación: lo toma de {@code X-Correlation-ID} (o genera
 * uno), lo pone en el MDC para que salga en cada línea de log, lo añade como atributo del
 * span de OpenTelemetry y lo devuelve en la respuesta.
 *
 * <p>Spring Boot: equivale a un {@code OncePerRequestFilter}. En Quarkus REST se declara
 * con métodos {@code @ServerRequestFilter}/{@code @ServerResponseFilter}, sin implementar
 * interfaces. El MDC de Quarkus se guarda en el contexto de Vert.x, así que sigue a la
 * petición aunque cambie de hilo (event loop → worker o hilo virtual).
 */
public class CorrelationIdFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @ServerRequestFilter(preMatching = true)
    public void onRequest(ContainerRequestContext request) {
        String incoming = request.getHeaderString(HEADER);
        String correlationId = incoming != null && VALID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        request.setProperty(MDC_KEY, correlationId);
        MDC.put(MDC_KEY, correlationId);
        Span.current().setAttribute("correlation.id", correlationId);
    }

    @ServerResponseFilter
    public void onResponse(ContainerRequestContext request, ContainerResponseContext response) {
        Object correlationId = request.getProperty(MDC_KEY);
        if (correlationId != null) {
            response.getHeaders().putSingle(HEADER, correlationId);
        }
        MDC.remove(MDC_KEY);
    }

    /** Identificador de la petición en curso (o {@code null} fuera de una petición). */
    public static String current() {
        Object value = MDC.get(MDC_KEY);
        return value == null ? null : value.toString();
    }
}
