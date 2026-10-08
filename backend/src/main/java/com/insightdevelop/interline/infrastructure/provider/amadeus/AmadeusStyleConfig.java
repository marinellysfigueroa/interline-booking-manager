package com.insightdevelop.interline.infrastructure.provider.amadeus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Configuración del adaptador estilo Amadeus ({@code app.amadeus-style.*}). Las credenciales
 * llegan por variables de entorno (Secret Manager en GCP); nunca se versionan.
 *
 * <p>Spring Boot: {@code @ConfigurationProperties(prefix = "app.amadeus-style")}.
 */
@ConfigMapping(prefix = "app.amadeus-style")
public interface AmadeusStyleConfig {

    /** Opcional para que la app arranque con el proveedor {@code simulated}; se exige al pedir token. */
    Optional<String> clientId();

    Optional<String> clientSecret();

    /** Moneda en la que se piden los precios. */
    @WithDefault("USD")
    String currency();

    @WithDefault("20")
    int maxResults();

    /**
     * Millas por unidad de moneda para calcular {@code milesEquivalent}. El contrato estilo
     * Amadeus no trae precio en millas: es una regla propia del programa de lealtad.
     */
    @WithDefault("100")
    BigDecimal milesPerCurrencyUnit();

    /** Vigencia de una oferta para reservarla (el contrato no trae caducidad propia). */
    @WithDefault("PT30M")
    Duration offerTtl();

    /** Margen para renovar el token antes de que caduque. */
    @WithDefault("PT60S")
    Duration tokenExpirySkew();
}
