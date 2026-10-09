package com.insightdevelop.interline.application.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;

/**
 * Configuración tipada de la saga ({@code app.saga.*}).
 *
 * <p>Spring Boot: equivale a {@code @ConfigurationProperties(prefix = "app.saga")}. En
 * Quarkus es una interfaz; SmallRye Config genera la implementación y valida al arrancar.
 */
@ConfigMapping(prefix = "app.saga")
public interface SagaConfig {

    SegmentConfirmation segmentConfirmation();

    interface SegmentConfirmation {

        /** Consultas a la operadora antes de dar un segmento UC por no confirmado. */
        @WithDefault("3")
        int maxAttempts();

        @WithDefault("PT0.5S")
        Duration initialDelay();

        @WithDefault("2.0")
        double backoffMultiplier();
    }
}
