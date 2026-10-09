package com.insightdevelop.interline.domain.booking;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "value");
    }

    public static PaymentId random() {
        return new PaymentId(UUID.randomUUID());
    }
}
