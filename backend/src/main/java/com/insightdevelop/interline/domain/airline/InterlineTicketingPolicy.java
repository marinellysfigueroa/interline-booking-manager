package com.insightdevelop.interline.domain.airline;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de dominio con la regla central del negocio: <b>solo se emite un ticket
 * único si la aerolínea validadora tiene acuerdo interline con todas las operadoras</b>.
 *
 * <p>Es una clase sin estado y sin anotaciones. La capa de aplicación la publica como
 * bean con un productor CDI ({@code @Produces @ApplicationScoped}), igual que harías
 * en Spring con un {@code @Bean} en una {@code @Configuration} para no contaminar el
 * dominio con {@code @Service}.
 */
public final class InterlineTicketingPolicy {

    public InterlineEligibility evaluate(Airline validatingAirline, Collection<AirlineCode> operatingCarriers) {
        Objects.requireNonNull(validatingAirline, "validatingAirline");
        Set<AirlineCode> operating = new LinkedHashSet<>(operatingCarriers);
        Set<AirlineCode> missing = operating.stream()
                .filter(carrier -> !validatingAirline.hasInterlineAgreementWith(carrier))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new InterlineEligibility(validatingAirline.code(), operating, missing);
    }
}
