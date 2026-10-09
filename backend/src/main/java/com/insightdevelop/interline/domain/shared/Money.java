package com.insightdevelop.interline.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Importe monetario no negativo en una moneda ISO 4217.
 *
 * <p>La escala se normaliza a los decimales de la moneda (USD 2, COP 2, JPY 0...).
 * Si el importe trae más decimales de los permitidos se rechaza en lugar de
 * redondear en silencio.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("El importe no puede ser negativo: " + amount);
        }
        try {
            amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "%s admite %d decimales: %s".formatted(currency, currency.getDefaultFractionDigits(), amount), e);
        }
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean hasSameCurrencyAs(Money other) {
        return currency.equals(other.currency);
    }

    public void requireSameCurrency(Money other) {
        if (!hasSameCurrencyAs(other)) {
            throw new BusinessRuleViolationException(DomainErrorCode.CURRENCY_MISMATCH,
                    "Monedas distintas: %s y %s".formatted(currency, other.currency));
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
