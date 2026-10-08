/**
 * Agregado {@code Booking}.
 *
 * <p>{@link com.insightdevelop.interline.domain.booking.Booking} es la raíz del agregado;
 * {@code Segment} y {@code Payment} son entidades internas cuyos métodos de cambio de
 * estado son <i>package-private</i>: solo la raíz puede modificarlas, así se garantizan
 * las invariantes (p. ej. que una reserva {@code FAILED} no retenga inventario ni fondos).
 */
package com.insightdevelop.interline.domain.booking;
