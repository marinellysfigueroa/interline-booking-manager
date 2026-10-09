package com.insightdevelop.interline.infrastructure.rest.problem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.insightdevelop.interline.application.booking.BookingSagaFailedException;
import com.insightdevelop.interline.domain.shared.DomainException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Traduce excepciones a Problem Details.
 *
 * <p>Spring Boot: equivale a un {@code @RestControllerAdvice} con métodos
 * {@code @ExceptionHandler}. En Quarkus REST cada método anotado con
 * {@code @ServerExceptionMapper} registra un mapper para los tipos de su parámetro.
 */
public class ProblemExceptionMappers {

    private final ProblemResponses problems;

    ProblemExceptionMappers(ProblemResponses problems) {
        this.problems = problems;
    }

    @ServerExceptionMapper
    public Response domain(DomainException e, UriInfo uri) {
        return problems.from(e, uri.getPath());
    }

    @ServerExceptionMapper
    public Response saga(BookingSagaFailedException e, UriInfo uri) {
        return problems.from(e, uri.getPath());
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e, UriInfo uri) {
        return problems.from(e, uri.getPath());
    }

    @ServerExceptionMapper
    public Response invalidRequest(InvalidRequestException e, UriInfo uri) {
        return problems.from(e, uri.getPath());
    }

    @ServerExceptionMapper
    public Response api(ApiProblemException e, UriInfo uri) {
        return problems.from(e, uri.getPath());
    }

    /** Se declaran ambos tipos para tener prioridad sobre el mapper de Jackson incluido en Quarkus. */
    @ServerExceptionMapper({MismatchedInputException.class, JsonProcessingException.class})
    public Response json(JsonProcessingException e, UriInfo uri) {
        return problems.from(e, uri.getPath());
    }

    @ServerExceptionMapper
    public Response illegalArgument(IllegalArgumentException e, UriInfo uri) {
        return problems.from(e, uri.getPath());
    }

    @ServerExceptionMapper
    public Response web(WebApplicationException e, UriInfo uri) {
        if (e.getResponse().getStatus() < 400) {
            return e.getResponse();
        }
        return problems.from(e, uri.getPath());
    }

    @ServerExceptionMapper
    public Response unexpected(Exception e, UriInfo uri) {
        return problems.from(e, uri.getPath());
    }
}
