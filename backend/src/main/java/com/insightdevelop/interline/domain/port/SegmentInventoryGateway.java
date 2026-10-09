package com.insightdevelop.interline.domain.port;

import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.booking.SegmentStatus;

/**
 * Inventario de las aerolíneas operadoras. Todas las operaciones deben ser
 * <b>idempotentes</b> por (localizador, segmento): la saga puede repetirlas.
 */
public interface SegmentInventoryGateway {

    /** Solicita plazas; la operadora responde HK, UC o XX. */
    SegmentStatus sell(BookingLocator locator, Segment segment, int seats);

    /** Consulta el estado actual de un segmento solicitado (para reintentar los UC). */
    SegmentStatus checkStatus(BookingLocator locator, Segment segment);

    /** Cancela el segmento; cancelar uno ya cancelado no falla. */
    void cancel(BookingLocator locator, Segment segment);
}
