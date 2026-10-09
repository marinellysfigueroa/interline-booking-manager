package com.insightdevelop.interline.domain.booking;

import java.util.Objects;
import java.util.UUID;

public record PassengerId(UUID value) {

    public PassengerId {
        Objects.requireNonNull(value, "value");
    }

    public static PassengerId random() {
        return new PassengerId(UUID.randomUUID());
    }
}
