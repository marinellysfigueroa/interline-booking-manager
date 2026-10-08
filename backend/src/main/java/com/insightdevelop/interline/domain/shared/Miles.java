package com.insightdevelop.interline.domain.shared;

/** Cantidad no negativa de millas de un programa de lealtad. */
public record Miles(long value) implements Comparable<Miles> {

    public static final Miles ZERO = new Miles(0);

    public Miles {
        if (value < 0) {
            throw new IllegalArgumentException("Las millas no pueden ser negativas: " + value);
        }
    }

    public static Miles of(long value) {
        return new Miles(value);
    }

    public boolean isPositive() {
        return value > 0;
    }

    public Miles plus(Miles other) {
        return new Miles(Math.addExact(value, other.value));
    }

    @Override
    public int compareTo(Miles other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return value + " millas";
    }
}
