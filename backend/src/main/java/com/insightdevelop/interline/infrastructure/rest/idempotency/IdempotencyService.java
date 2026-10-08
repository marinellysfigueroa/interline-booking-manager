package com.insightdevelop.interline.infrastructure.rest.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.insightdevelop.interline.infrastructure.persistence.IdempotencyRecordPanacheRepository;
import com.insightdevelop.interline.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.insightdevelop.interline.infrastructure.rest.problem.ApiProblemException;
import com.insightdevelop.interline.infrastructure.rest.problem.ProblemResponses;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import org.jboss.logging.Logger;

/**
 * Implementa {@code Idempotency-Key} (draft-ietf-httpapi-idempotency-key-header) sobre
 * PostgreSQL, así que funciona con varias instancias en Cloud Run.
 *
 * <ol>
 *   <li>Reserva la clave en una transacción propia ({@code IN_PROGRESS}). La clave
 *       primaria (scope, key) garantiza que solo una petición la gane.</li>
 *   <li>Misma clave + mismo hash del cuerpo + {@code COMPLETED} → repite la respuesta
 *       guardada con {@code Idempotency-Replayed: true}.</li>
 *   <li>Misma clave + otro cuerpo → 422. Clave aún {@code IN_PROGRESS} → 409.</li>
 *   <li>Ejecuta la operación y guarda la respuesta si es determinista (2xx o 4xx salvo
 *       409); ante 5xx o 409 borra la clave para permitir reintentar.</li>
 * </ol>
 */
@ApplicationScoped
public class IdempotencyService {

    public static final String REPLAYED_HEADER = "Idempotency-Replayed";
    /** Una petición IN_PROGRESS más antigua que esto se considera abandonada (proceso caído). */
    static final Duration STALE_AFTER = Duration.ofMinutes(2);
    private static final Logger LOG = Logger.getLogger(IdempotencyService.class);

    private final IdempotencyRecordPanacheRepository records;
    private final ProblemResponses problems;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;
    private final Clock clock;

    IdempotencyService(IdempotencyRecordPanacheRepository records, ProblemResponses problems,
            ObjectMapper objectMapper, Clock clock) {
        this.records = records;
        this.problems = problems;
        this.objectMapper = objectMapper;
        this.clock = clock;
        // JSON canónico (propiedades ordenadas) para que el hash no dependa del orden de los campos
        this.canonicalMapper = JsonMapper.builder()
                .findAndAddModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    /**
     * @param scope   operación + recurso, p. ej. {@code POST /api/v1/bookings}
     * @param request cuerpo de la petición (se usa su hash)
     * @param path    ruta, para el {@code instance} de los Problem Details
     */
    public Response execute(String scope, String key, Object request, String path, Supplier<Response> action) {
        var id = new IdempotencyRecordEntity.Key(scope, key);
        String hash = hash(request);
        Optional<Response> replay = claim(id, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        Response response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            response = problems.from(e, path);
        }
        int status = response.getStatus();
        if (status < 500 && status != 409) {
            complete(id, response);
        } else {
            release(id);
        }
        return Response.fromResponse(response).header(REPLAYED_HEADER, false).build();
    }

    private Optional<Response> claim(IdempotencyRecordEntity.Key id, String hash) {
        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                Instant now = clock.instant();
                IdempotencyRecordEntity record = records.findById(id);
                if (record == null) {
                    record = new IdempotencyRecordEntity();
                    record.id = id;
                    record.requestHash = hash;
                    record.status = "IN_PROGRESS";
                    record.createdAt = now;
                    record.updatedAt = now;
                    records.persistAndFlush(record);
                    return Optional.<Response>empty();
                }
                if (!record.requestHash.equals(hash)) {
                    throw new ApiProblemException(422, "IDEMPOTENCY_KEY_REUSED",
                            "La Idempotency-Key ya se usó con un cuerpo de petición distinto");
                }
                if ("COMPLETED".equals(record.status)) {
                    return Optional.of(replay(record));
                }
                if (record.updatedAt.isAfter(now.minus(STALE_AFTER))) {
                    throw inProgress();
                }
                LOG.warnf("Retomando petición idempotente abandonada %s", id);
                record.updatedAt = now;
                return Optional.<Response>empty();
            });
        } catch (PersistenceException e) {
            // Otra petición insertó la misma clave a la vez (violación de la clave primaria).
            throw inProgress();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof PersistenceException) {
                throw inProgress();
            }
            throw e;
        }
    }

    private void complete(IdempotencyRecordEntity.Key id, Response response) {
        QuarkusTransaction.requiringNew().run(() -> {
            IdempotencyRecordEntity record = records.findById(id);
            record.status = "COMPLETED";
            record.responseStatus = response.getStatus();
            record.responseBody = response.hasEntity() ? write(response.getEntity()) : null;
            record.contentType = response.getMediaType() == null ? null : response.getMediaType().toString();
            record.location = response.getHeaderString(HttpHeaders.LOCATION);
            record.updatedAt = clock.instant();
        });
    }

    private void release(IdempotencyRecordEntity.Key id) {
        QuarkusTransaction.requiringNew().run(() -> records.deleteById(id));
    }

    private static Response replay(IdempotencyRecordEntity record) {
        Response.ResponseBuilder builder = Response.status(record.responseStatus)
                .header(REPLAYED_HEADER, true);
        if (record.responseBody != null) {
            builder.entity(record.responseBody).type(record.contentType);
        }
        if (record.location != null) {
            builder.header(HttpHeaders.LOCATION, record.location);
        }
        return builder.build();
    }

    private static ApiProblemException inProgress() {
        return new ApiProblemException(409, "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "Ya hay una petición en curso con esta Idempotency-Key");
    }

    private String hash(Object request) {
        try {
            byte[] json = canonicalMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object entity) {
        try {
            return entity instanceof String s ? s : objectMapper.writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
