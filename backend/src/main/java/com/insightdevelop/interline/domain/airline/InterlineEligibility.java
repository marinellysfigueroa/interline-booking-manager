package com.insightdevelop.interline.domain.airline;

import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import com.insightdevelop.interline.domain.shared.DomainErrorCode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Resultado de evaluar si un itinerario puede emitirse en un solo ticket. */
public record InterlineEligibility(
        AirlineCode validatingCarrier,
        Set<AirlineCode> operatingCarriers,
        Set<AirlineCode> missingAgreements) {

    public InterlineEligibility {
        Objects.requireNonNull(validatingCarrier, "validatingCarrier");
        // conjuntos inmutables que conservan el orden de vuelo
        operatingCarriers = Collections.unmodifiableSet(new LinkedHashSet<>(operatingCarriers));
        missingAgreements = Collections.unmodifiableSet(new LinkedHashSet<>(missingAgreements));
    }

    public boolean singleTicketEligible() {
        return missingAgreements.isEmpty();
    }

    /** @throws BusinessRuleViolationException {@code INTERLINE_AGREEMENT_MISSING} si falta algún acuerdo */
    public void requireEligible() {
        if (!singleTicketEligible()) {
            String missing = missingAgreements.stream()
                    .map(AirlineCode::value)
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new BusinessRuleViolationException(DomainErrorCode.INTERLINE_AGREEMENT_MISSING,
                    "La validadora %s no tiene acuerdo interline con: %s".formatted(validatingCarrier, missing));
        }
    }
}
