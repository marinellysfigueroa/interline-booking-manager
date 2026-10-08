package com.insightdevelop.interline.application.config;

import com.insightdevelop.interline.domain.airline.InterlineTicketingPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

/**
 * Publica como beans CDI objetos que no llevan anotaciones (el dominio es Java puro).
 *
 * <p>Spring Boot: equivale a una clase {@code @Configuration} con métodos {@code @Bean}.
 * En Quarkus basta con métodos {@code @Produces} en cualquier bean; ArC los resuelve en
 * tiempo de build.
 */
@ApplicationScoped
public class DomainServicesProducer {

    @Produces
    @ApplicationScoped
    InterlineTicketingPolicy interlineTicketingPolicy() {
        return new InterlineTicketingPolicy();
    }

    /** Reloj inyectable: las pruebas pueden sustituirlo para controlar el tiempo. */
    @Produces
    @ApplicationScoped
    Clock clock() {
        return Clock.systemUTC();
    }
}
