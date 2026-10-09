package com.insightdevelop.interline.infrastructure.gateway.simulated;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;
import java.util.Set;

/**
 * Comportamiento de los sistemas externos simulados ({@code app.simulated.*}), para
 * poder reproducir en local los caminos de la saga (UC que se confirma, UC que nunca se
 * confirma, rechazo XX).
 */
@ConfigMapping(prefix = "app.simulated")
public interface SimulatedGatewaysConfig {

    Inventory inventory();

    interface Inventory {

        /** Operadoras que responden UC al vender (se confirman tras {@link #confirmAfterChecks()} consultas). */
        Optional<Set<String>> unconfirmedCarriers();

        /** Operadoras que responden UC y nunca confirman (dispara la compensación). */
        Optional<Set<String>> neverConfirmCarriers();

        /** Operadoras que rechazan la venta (XX). */
        Optional<Set<String>> rejectedCarriers();

        @WithDefault("1")
        int confirmAfterChecks();
    }
}
