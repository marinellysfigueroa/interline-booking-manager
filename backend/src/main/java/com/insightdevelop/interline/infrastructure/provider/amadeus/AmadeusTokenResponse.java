package com.insightdevelop.interline.infrastructure.provider.amadeus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta OAuth2 (client credentials) tal como la definen nuestros stubs de WireMock.
 * Solo se mapean los campos que usamos; el resto se ignora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AmadeusTokenResponse(
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        // Campo asumido: "approved" cuando el token es utilizable (definido en nuestros stubs).
        @JsonProperty("state") String state) {
}
