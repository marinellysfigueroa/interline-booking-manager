package com.insightdevelop.interline.domain.booking;

/**
 * Estado de un segmento con los códigos de estado de los GDS.
 *
 * <p>Transiciones: {@code UC → HK}, {@code UC → XX}, {@code HK → XX}. {@code XX} es terminal.
 * Pasar al mismo estado es un no-op (idempotencia de la saga).
 */
public enum SegmentStatus {
    /** Holding confirmed: plaza confirmada por la aerolínea operadora. */
    HK,
    /** Unable to confirm: solicitada pero aún no confirmada. */
    UC,
    /** Cancelado. */
    XX;

    public boolean canTransitionTo(SegmentStatus target) {
        return switch (this) {
            case UC -> target == HK || target == XX;
            case HK -> target == XX;
            case XX -> false;
        };
    }
}
