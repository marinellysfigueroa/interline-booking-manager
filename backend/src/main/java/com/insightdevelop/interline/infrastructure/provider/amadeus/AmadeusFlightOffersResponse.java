package com.insightdevelop.interline.infrastructure.provider.amadeus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Contrato de búsqueda de ofertas <b>estilo</b> Amadeus Flight Offers Search, limitado a los
 * campos que definen nuestros stubs ({@code /wiremock/__files}). No es el contrato oficial:
 * cualquier campo nuevo debe añadirse primero a los stubs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AmadeusFlightOffersResponse(Meta meta, List<Offer> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(int count) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Offer(
            String id,
            // Fecha límite de emisión según el proveedor (informativa; no se usa como caducidad).
            String lastTicketingDate,
            List<Itinerary> itineraries,
            Price price,
            List<String> validatingAirlineCodes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Itinerary(String duration, List<Segment> segments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(
            Endpoint departure,
            Endpoint arrival,
            /** Aerolínea comercializadora. */
            String carrierCode,
            String number,
            /** Aerolínea operadora (puede faltar si coincide con la comercializadora; asumido). */
            Operating operating) {
    }

    /** {@code at} es hora LOCAL del aeropuerto sin offset (forma asumida en los stubs). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Endpoint(String iataCode, String at) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operating(String carrierCode) {
    }

    /** Importes como texto decimal; {@code grandTotal} es el total para todos los pasajeros. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(String currency, String total, String grandTotal) {
    }
}
