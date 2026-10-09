package com.insightdevelop.interline.domain.shared;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Página de resultados (equivalente a {@code org.springframework.data.domain.Page}). */
public record Page<T>(List<T> items, int page, int size, long totalElements) {

    public Page {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements debe ser >= 0");
        }
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        return new Page<>(items.stream().<R>map(mapper).toList(), page, size, totalElements);
    }
}
