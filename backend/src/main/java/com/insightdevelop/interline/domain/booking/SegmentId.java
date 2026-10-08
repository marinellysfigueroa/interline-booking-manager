package com.insightdevelop.interline.domain.booking;

import java.util.Objects;
import java.util.UUID;

public record SegmentId(UUID value) {

    public SegmentId {
        Objects.requireNonNull(value, "value");
    }

    public static SegmentId random() {
        return new SegmentId(UUID.randomUUID());
    }
}
