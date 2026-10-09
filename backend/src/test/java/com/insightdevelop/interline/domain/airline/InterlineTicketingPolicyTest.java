package com.insightdevelop.interline.domain.airline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insightdevelop.interline.domain.shared.BusinessRuleViolationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InterlineTicketingPolicyTest {

    private static final AirlineCode AV = AirlineCode.of("AV");
    private static final AirlineCode IB = AirlineCode.of("IB");
    private static final AirlineCode AZ = AirlineCode.of("AZ");
    private static final AirlineCode LA = AirlineCode.of("LA");

    private final InterlineTicketingPolicy policy = new InterlineTicketingPolicy();
    private final Airline avianca = new Airline(AV, "Avianca", "134", Optional.empty(), Set.of(IB, AZ));

    @Test
    void own_flights_never_need_an_agreement() {
        assertThat(policy.evaluate(avianca, List.of(AV)).singleTicketEligible()).isTrue();
    }

    @Test
    void eligible_when_validating_carrier_has_agreements_with_all_operating_carriers() {
        var result = policy.evaluate(avianca, List.of(AV, IB, AZ));

        assertThat(result.singleTicketEligible()).isTrue();
        assertThat(result.missingAgreements()).isEmpty();
    }

    @Test
    void lists_every_missing_agreement() {
        var result = policy.evaluate(avianca, List.of(AV, IB, LA));

        assertThat(result.singleTicketEligible()).isFalse();
        assertThat(result.missingAgreements()).containsExactly(LA);
        assertThatThrownBy(result::requireEligible)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("LA");
    }

    @Test
    void agreements_are_directional() {
        Airline iberia = new Airline(IB, "Iberia", "075", Optional.empty(), Set.of());

        assertThat(policy.evaluate(avianca, List.of(IB)).singleTicketEligible()).isTrue();
        assertThat(policy.evaluate(iberia, List.of(AV)).singleTicketEligible()).isFalse();
    }

    @Test
    void an_airline_cannot_be_its_own_partner() {
        assertThatThrownBy(() -> new Airline(AV, "Avianca", "134", Optional.empty(), Set.of(AV)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validates_iata_airline_codes() {
        assertThat(AirlineCode.of(" 4c ").value()).isEqualTo("4C");
        assertThatThrownBy(() -> AirlineCode.of("12")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AirlineCode.of("AVA")).isInstanceOf(IllegalArgumentException.class);
    }
}
