package com.insightdevelop.interline.infrastructure.rest;

import com.insightdevelop.interline.infrastructure.rest.dto.BookingActionDto;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PassengerTypeDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentMethodDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.SegmentStatusDto;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Los recursos devuelven {@code Response}, así que Quarkus no deduce qué DTO se serializan.
 * Las clases DTO llevan {@code @RegisterForReflection} desde openapi-generator
 * ({@code additionalModelTypeAnnotations}), pero el generador no anota los enums: se registran
 * aquí. {@code NativeReflectionCoverageTest} falla si alguno queda fuera.
 */
@RegisterForReflection(targets = {
        BookingActionDto.class, BookingStatusDto.class, PassengerTypeDto.class, PaymentMethodDto.class,
        PaymentStatusDto.class, SegmentStatusDto.class
})
public final class RestNativeReflectionConfig {

    private RestNativeReflectionConfig() {
    }
}
