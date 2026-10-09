package com.insightdevelop.interline.domain.booking;

import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import com.insightdevelop.interline.domain.shared.InvalidStateTransitionException;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Pago de una reserva (entidad interna del agregado).
 *
 * <p>Un pago {@code MIXED} agrupa en una sola entidad la autorización en la pasarela y
 * el hold de millas: se capturan o liberan juntos. Las referencias externas permiten
 * que los pasos de captura/liberación de la saga sean idempotentes contra la pasarela
 * y el programa de lealtad.
 */
public final class Payment {

    private final PaymentId id;
    private final PaymentMethod method;
    private final Money cash;
    private final Miles miles;
    private final String memberNumber;
    private final String cashAuthorizationRef;
    private final String milesHoldRef;
    private final Instant createdAt;
    private PaymentStatus status;

    private Payment(PaymentId id, PaymentMethod method, Money cash, Miles miles, String memberNumber,
            String cashAuthorizationRef, String milesHoldRef, Instant createdAt, PaymentStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.method = Objects.requireNonNull(method, "method");
        this.miles = Objects.requireNonNull(miles, "miles");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = Objects.requireNonNull(status, "status");
        this.cash = cash;
        this.memberNumber = memberNumber;
        this.cashAuthorizationRef = cashAuthorizationRef;
        this.milesHoldRef = milesHoldRef;
    }

    /**
     * Crea un pago ya autorizado por los sistemas externos.
     *
     * @param cash                 importe en dinero; {@code null} si {@code method == MILES}
     * @param miles                millas; {@link Miles#ZERO} si {@code method == CASH}
     * @param memberNumber         número de socio; obligatorio si se usan millas
     * @param cashAuthorizationRef referencia de la pasarela; obligatoria si se usa dinero
     * @param milesHoldRef         referencia del hold de millas; obligatoria si se usan millas
     */
    public static Payment authorized(PaymentMethod method, Money cash, Miles miles, String memberNumber,
            String cashAuthorizationRef, String milesHoldRef, Instant at) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(miles, "miles");
        boolean hasCash = cash != null && cash.isPositive();
        boolean hasMiles = miles.isPositive();
        if (method.usesCash() != hasCash || method.usesMiles() != hasMiles) {
            throw new BusinessRuleViolationException(DomainErrorCode.INVALID_PAYMENT,
                    "Un pago %s %s dinero y %s millas".formatted(method,
                            method.usesCash() ? "requiere" : "no admite",
                            method.usesMiles() ? "requiere" : "no admite"));
        }
        if (hasCash && isBlank(cashAuthorizationRef)) {
            throw new IllegalArgumentException("Falta la referencia de autorización de la pasarela");
        }
        if (hasMiles && (isBlank(memberNumber) || isBlank(milesHoldRef))) {
            throw new IllegalArgumentException("Faltan el número de socio o la referencia del hold de millas");
        }
        return new Payment(PaymentId.random(), method, hasCash ? cash : null, miles, memberNumber,
                cashAuthorizationRef, milesHoldRef, at, PaymentStatus.AUTHORIZED);
    }

    /** Reconstrucción desde persistencia (no aplica reglas de creación). */
    public static Payment restore(PaymentId id, PaymentMethod method, Money cash, Miles miles, String memberNumber,
            String cashAuthorizationRef, String milesHoldRef, Instant createdAt, PaymentStatus status) {
        return new Payment(id, method, cash, miles, memberNumber, cashAuthorizationRef, milesHoldRef, createdAt,
                status);
    }

    /** @return {@code true} si el estado cambió (idempotente) */
    boolean capture() {
        return moveTo(PaymentStatus.CAPTURED);
    }

    /** @return {@code true} si el estado cambió (idempotente) */
    boolean release() {
        return moveTo(PaymentStatus.RELEASED);
    }

    private boolean moveTo(PaymentStatus target) {
        if (status == target) {
            return false;
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Pago", status, target);
        }
        status = target;
        return true;
    }

    /** Un pago autorizado o capturado compromete fondos o millas del cliente. */
    public boolean holdsFunds() {
        return status != PaymentStatus.RELEASED;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public PaymentId id() {
        return id;
    }

    public PaymentMethod method() {
        return method;
    }

    public Optional<Money> cash() {
        return Optional.ofNullable(cash);
    }

    public Miles miles() {
        return miles;
    }

    public Optional<String> memberNumber() {
        return Optional.ofNullable(memberNumber);
    }

    public Optional<String> cashAuthorizationRef() {
        return Optional.ofNullable(cashAuthorizationRef);
    }

    public Optional<String> milesHoldRef() {
        return Optional.ofNullable(milesHoldRef);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public PaymentStatus status() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Payment other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
