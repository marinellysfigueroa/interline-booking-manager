package com.insightdevelop.interline.infrastructure.rest.problem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.insightdevelop.interline.application.booking.BookingQueries;
import com.insightdevelop.interline.application.booking.BookingSagaFailedException;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainException;
import com.insightdevelop.interline.domain.shared.ExternalServiceUnavailableException;
import com.insightdevelop.interline.domain.shared.InvalidStateTransitionException;
import com.insightdevelop.interline.domain.shared.ResourceNotFoundException;
import com.insightdevelop.interline.infrastructure.rest.correlation.CorrelationIdFilter;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.FieldErrorDto;
import com.insightdevelop.interline.infrastructure.rest.dto.ProblemDto;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hibernate.StaleStateException;
import org.jboss.logging.Logger;

/**
 * Construye respuestas Problem Details (RFC 7807) a partir de cualquier excepción. Lo
 * usan los {@code ExceptionMapper} y el servicio de idempotencia (que guarda la respuesta
 * de error para repetirla).
 */
@ApplicationScoped
public class ProblemResponses {

    public static final String MEDIA_TYPE = "application/problem+json";
    private static final String TYPE_BASE = "https://interline.example.com/problems/";
    private static final Logger LOG = Logger.getLogger(ProblemResponses.class);
    private static final Pattern LOCATOR_PATH = Pattern.compile("/api/v1/bookings/([A-Za-z0-9]{6})(/.*)?$");

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("VALIDATION_ERROR", "Petición inválida"),
            Map.entry("INVALID_REQUEST", "Petición inválida"),
            Map.entry("NO_ADULT_PASSENGER", "Se necesita un adulto"),
            Map.entry("TOO_MANY_PASSENGERS", "Demasiados pasajeros"),
            Map.entry("INFANT_RULE_VIOLATION", "Regla de infantes"),
            Map.entry("PASSENGER_AGE_MISMATCH", "Edad incompatible con el tipo de pasajero"),
            Map.entry("PASSENGER_MISMATCH", "Pasajeros distintos de la oferta"),
            Map.entry("INVALID_ITINERARY", "Itinerario inválido"),
            Map.entry("OFFER_EXPIRED", "Oferta caducada"),
            Map.entry("SEGMENT_REJECTED", "Segmento rechazado"),
            Map.entry("SEGMENTS_NOT_CONFIRMED", "Segmentos sin confirmar"),
            Map.entry("SEGMENT_NOT_CONFIRMED", "Segmento no confirmado"),
            Map.entry("INTERLINE_AGREEMENT_MISSING", "Sin acuerdo interline"),
            Map.entry("VALIDATING_AIRLINE_MISMATCH", "Aerolínea validadora distinta"),
            Map.entry("INVALID_PAYMENT", "Pago inválido"),
            Map.entry("CURRENCY_MISMATCH", "Moneda distinta"),
            Map.entry("INSUFFICIENT_PAYMENT", "Pago insuficiente"),
            Map.entry("PAYMENT_NOT_CAPTURED", "Pago no capturado"),
            Map.entry("RESOURCES_STILL_HELD", "La reserva aún retiene recursos"),
            Map.entry("INVALID_STATE_TRANSITION", "Transición de estado no permitida"),
            Map.entry("CONCURRENT_MODIFICATION", "Modificación concurrente"),
            Map.entry("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key reutilizada"),
            Map.entry("IDEMPOTENCY_REQUEST_IN_PROGRESS", "Petición en curso"),
            Map.entry("BOOKING_NOT_FOUND", "Reserva no encontrada"),
            Map.entry("OFFER_NOT_FOUND", "Oferta no encontrada"),
            Map.entry("AIRLINE_NOT_FOUND", "Aerolínea no encontrada"),
            Map.entry("SEGMENT_NOT_FOUND", "Segmento no encontrado"),
            Map.entry("PAYMENT_NOT_FOUND", "Pago no encontrado"),
            Map.entry("PROVIDER_UNAVAILABLE", "Proveedor no disponible"),
            Map.entry("UNAUTHORIZED", "No autenticado"),
            Map.entry("FORBIDDEN", "Sin permiso"),
            Map.entry("INTERNAL_ERROR", "Error interno"));

    private final BookingQueries bookingQueries;

    ProblemResponses(BookingQueries bookingQueries) {
        this.bookingQueries = bookingQueries;
    }

    /** @param path ruta de la petición ({@code instance}); permite añadir el estado de la reserva */
    public Response from(Throwable error, String path) {
        return switch (error) {
            case BookingSagaFailedException e -> {
                ProblemDto problem = problem(statusOf(e.reason()), e.reason().code().name(), e.getMessage(), path)
                        .locator(e.locator().value())
                        .bookingStatus(BookingStatusDto.valueOf(e.bookingStatus().name()));
                yield build(problem);
            }
            case DomainException e -> {
                int status = statusOf(e);
                ProblemDto problem = problem(status, e.code().name(), e.getMessage(), path);
                if (status == 409 || status == 422) {
                    currentBookingStatus(path).ifPresent(s -> problem.bookingStatus(BookingStatusDto.valueOf(s.name())));
                }
                Response.ResponseBuilder response = Response.status(status).type(MEDIA_TYPE).entity(problem);
                if (e instanceof ExternalServiceUnavailableException) {
                    response.header("Retry-After", 5);
                }
                yield response.build();
            }
            case ConstraintViolationException e -> fromViolations(e, path);
            case InvalidRequestException e -> build(problem(400, "VALIDATION_ERROR", "La petición no es válida", path)
                    .errors(List.of(new FieldErrorDto(e.field(), e.getMessage()))));
            case ApiProblemException e -> build(problem(e.status(), e.code(), e.getMessage(), path));
            case JsonProcessingException e -> fromJson(e, path);
            case AuthenticationFailedException e -> unauthorized(path);
            case UnauthorizedException e -> unauthorized(path);
            case ForbiddenException e -> build(problem(403, "FORBIDDEN", "No tienes permiso para esta operación", path));
            case IllegalArgumentException e -> build(problem(400, "INVALID_REQUEST", e.getMessage(), path));
            // JAX-RS responde 404 si no puede convertir un @QueryParam/@PathParam (p. ej. un enum
            // inválido); para el cliente es una petición mal formada, así que se devuelve 400.
            case NotFoundException e when e.getCause() != null -> build(problem(400, "INVALID_REQUEST",
                    "Parámetro con formato incorrecto: " + rootMessage(e), path));
            case WebApplicationException e -> {
                int status = e.getResponse().getStatus();
                yield build(problem(status, "HTTP_" + status, Response.Status.fromStatusCode(status) == null
                        ? e.getMessage() : Response.Status.fromStatusCode(status).getReasonPhrase(), path));
            }
            default -> fromUnexpected(error, path);
        };
    }

    private static Response unauthorized(String path) {
        return Response.status(401).type(MEDIA_TYPE).header("WWW-Authenticate", "Bearer")
                .entity(problem(401, "UNAUTHORIZED", "Se requiere un token de acceso válido", path)).build();
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private static int statusOf(DomainException e) {
        // switch exhaustivo sobre la jerarquía sellada: un subtipo nuevo no compila sin decidir su código
        return switch (e) {
            case BusinessRuleViolationException ignored -> 422;
            case InvalidStateTransitionException ignored -> 409;
            case ResourceNotFoundException ignored -> 404;
            case ExternalServiceUnavailableException ignored -> 503;
        };
    }

    private Response fromViolations(ConstraintViolationException e, String path) {
        boolean returnValue = e.getConstraintViolations().stream().anyMatch(v -> hasKind(v.getPropertyPath(),
                ElementKind.RETURN_VALUE));
        if (returnValue) {
            return fromUnexpected(e, path); // el servidor produjo una respuesta que no cumple el contrato
        }
        List<FieldErrorDto> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(fieldName(v), v.getMessage()))
                .sorted(Comparator.comparing(FieldErrorDto::getField))
                .toList();
        return build(problem(400, "VALIDATION_ERROR",
                "La petición contiene %d errores de validación".formatted(errors.size()), path).errors(errors));
    }

    private Response fromJson(JsonProcessingException e, String path) {
        String field = "body";
        if (e instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            field = jsonPath(mapping.getPath());
        }
        String message = e instanceof JsonMappingException ? "valor ausente o con formato incorrecto" : "JSON mal formado";
        return build(problem(400, "VALIDATION_ERROR", "El cuerpo de la petición no es válido", path)
                .errors(List.of(new FieldErrorDto(field, message))));
    }

    private Response fromUnexpected(Throwable error, String path) {
        if (causedByConcurrentModification(error)) {
            return build(problem(409, "CONCURRENT_MODIFICATION",
                    "La reserva fue modificada por otra petición; vuelve a consultarla", path));
        }
        LOG.errorf(error, "Error no controlado en %s", path);
        return build(problem(500, "INTERNAL_ERROR", "Error inesperado. Indica el correlationId al soporte.", path));
    }

    private Optional<BookingStatus> currentBookingStatus(String path) {
        if (path == null) {
            return Optional.empty();
        }
        var matcher = LOCATOR_PATH.matcher(path);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return bookingQueries.statusOf(BookingLocator.of(matcher.group(1)));
        } catch (RuntimeException ignored) {
            return Optional.empty(); // enriquecer el error nunca debe provocar otro error
        }
    }

    private static ProblemDto problem(int status, String code, String detail, String path) {
        return new ProblemDto(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')),
                TITLES.getOrDefault(code, Optional.ofNullable(Response.Status.fromStatusCode(status))
                        .map(Response.Status::getReasonPhrase).orElse("Error")), status)
                .detail(detail)
                .code(code)
                .instance(path)
                .correlationId(CorrelationIdFilter.current());
    }

    private static Response build(ProblemDto problem) {
        return Response.status(problem.getStatus()).type(MEDIA_TYPE).entity(problem).build();
    }

    private static boolean causedByConcurrentModification(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof OptimisticLockException || t instanceof StaleStateException) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasKind(Path path, ElementKind kind) {
        for (Path.Node node : path) {
            if (node.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code createBooking.createBookingRequestDto.passengers[0].firstName} →
     * {@code passengers[0].firstName}; {@code searchAirports.query} → {@code query}.
     */
    private static String fieldName(ConstraintViolation<?> violation) {
        List<String> parts = new ArrayList<>();
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.METHOD) {
                continue;
            }
            if (node.getKind() == ElementKind.PARAMETER && node.getName().endsWith("Dto")) {
                continue;
            }
            String name = node.getName();
            if (node.getIndex() != null && !parts.isEmpty()) {
                int last = parts.size() - 1;
                parts.set(last, parts.get(last) + "[" + node.getIndex() + "]");
            }
            if (name != null && !name.startsWith("<")) {
                parts.add(name);
            }
        }
        return parts.isEmpty() ? "body" : String.join(".", parts);
    }

    private static String jsonPath(List<JsonMappingException.Reference> path) {
        var sb = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            } else if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            }
        }
        return sb.isEmpty() ? "body" : sb.toString();
    }
}
