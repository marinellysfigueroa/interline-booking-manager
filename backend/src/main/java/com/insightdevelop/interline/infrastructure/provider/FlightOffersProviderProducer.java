package com.insightdevelop.interline.infrastructure.provider;

import com.insightdevelop.interline.domain.port.FlightOffersProvider;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Elige en <b>tiempo de ejecución</b> qué adaptador implementa el puerto
 * {@link FlightOffersProvider}, según {@code app.flight-offers.provider}.
 *
 * <p>Cada adaptador se registra con {@code @Identifier("nombre")}; este productor publica
 * el elegido como bean por defecto, que es lo que inyectan los casos de uso. Conectar un
 * proveedor real solo implica un adaptador nuevo con su {@code @Identifier}.
 *
 * <p>Spring Boot: sería {@code @ConditionalOnProperty} en cada adaptador. En Quarkus el
 * equivalente directo es {@code @IfBuildProperty}, pero se evalúa al compilar; con este
 * productor la misma imagen Docker puede cambiar de proveedor con una variable de entorno.
 */
@ApplicationScoped
public class FlightOffersProviderProducer {

    @Produces
    @ApplicationScoped
    FlightOffersProvider flightOffersProvider(
            @ConfigProperty(name = "app.flight-offers.provider") String providerName,
            @Any Instance<FlightOffersProvider> candidates) {
        Instance<FlightOffersProvider> selected = candidates.select(Identifier.Literal.of(providerName));
        if (!selected.isResolvable()) {
            throw new IllegalStateException("Proveedor de ofertas desconocido: " + providerName);
        }
        return selected.get();
    }
}
