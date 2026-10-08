package com.insightdevelop.interline.domain.airline;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aerolínea con sus acuerdos interline.
 *
 * <p>Los acuerdos se modelan <b>desde la perspectiva de la aerolínea validadora</b>:
 * {@code interlinePartners} son las aerolíneas cuyos vuelos esta aerolínea puede
 * incluir en un ticket que ella emite. En la vida real los acuerdos son bilaterales
 * pero no siempre simétricos, por eso no se infiere la relación inversa.
 *
 * @param code              código IATA
 * @param name              nombre comercial
 * @param accountingCode    prefijo contable IATA de 3 dígitos (primeros dígitos del ticket)
 * @param loyaltyProgram    programa de lealtad; vacío si la aerolínea no tiene
 * @param interlinePartners aerolíneas con acuerdo interline (sin incluirse a sí misma)
 */
public record Airline(
        AirlineCode code,
        String name,
        String accountingCode,
        Optional<LoyaltyProgram> loyaltyProgram,
        Set<AirlineCode> interlinePartners) {

    private static final Pattern ACCOUNTING_CODE = Pattern.compile("^[0-9]{3}$");

    public Airline {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(accountingCode, "accountingCode");
        Objects.requireNonNull(loyaltyProgram, "loyaltyProgram");
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la aerolínea es obligatorio");
        }
        if (!ACCOUNTING_CODE.matcher(accountingCode).matches()) {
            throw new IllegalArgumentException("Prefijo contable inválido: " + accountingCode);
        }
        interlinePartners = Set.copyOf(interlinePartners);
        if (interlinePartners.contains(code)) {
            throw new IllegalArgumentException(code + " no puede ser partner interline de sí misma");
        }
    }

    /** Una aerolínea siempre puede emitir sus propios vuelos; para las demás hace falta acuerdo. */
    public boolean hasInterlineAgreementWith(AirlineCode operatingCarrier) {
        return code.equals(operatingCarrier) || interlinePartners.contains(operatingCarrier);
    }
}
