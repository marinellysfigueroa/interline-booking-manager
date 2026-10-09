package com.insightdevelop.interline.domain.shared;

/**
 * Catálogo estable de códigos de error de dominio.
 *
 * <p>Viaja en el campo {@code code} de los Problem Details (RFC 7807), de modo que el
 * frontend puede reaccionar a un código sin depender del texto del mensaje.
 */
public enum DomainErrorCode {
    // --- Reglas de negocio (422) ---
    NO_ADULT_PASSENGER,
    TOO_MANY_PASSENGERS,
    INFANT_RULE_VIOLATION,
    PASSENGER_AGE_MISMATCH,
    PASSENGER_MISMATCH,
    INVALID_ITINERARY,
    OFFER_EXPIRED,
    SEGMENT_REJECTED,
    SEGMENTS_NOT_CONFIRMED,
    SEGMENT_NOT_CONFIRMED,
    INTERLINE_AGREEMENT_MISSING,
    VALIDATING_AIRLINE_MISMATCH,
    INVALID_PAYMENT,
    CURRENCY_MISMATCH,
    INSUFFICIENT_PAYMENT,
    PAYMENT_NOT_CAPTURED,
    RESOURCES_STILL_HELD,

    // --- Conflicto de estado (409) ---
    INVALID_STATE_TRANSITION,

    // --- Recurso inexistente (404) ---
    BOOKING_NOT_FOUND,
    OFFER_NOT_FOUND,
    AIRLINE_NOT_FOUND,
    SEGMENT_NOT_FOUND,
    PAYMENT_NOT_FOUND,

    // --- Dependencia externa caída (503) ---
    PROVIDER_UNAVAILABLE
}
