package com.insightdevelop.interline.infrastructure.rest;

import com.insightdevelop.interline.application.booking.CreateBookingCommand;
import com.insightdevelop.interline.application.booking.PaymentCommand;
import com.insightdevelop.interline.application.offer.OfferSearchResult;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.airline.InterlineEligibility;
import com.insightdevelop.interline.domain.airport.Airport;
import com.insightdevelop.interline.domain.airport.AirportCode;
import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.Contact;
import com.insightdevelop.interline.domain.booking.Fare;
import com.insightdevelop.interline.domain.booking.PassengerCounts;
import com.insightdevelop.interline.domain.booking.PassengerId;
import com.insightdevelop.interline.domain.booking.PassengerType;
import com.insightdevelop.interline.domain.booking.Payment;
import com.insightdevelop.interline.domain.booking.PaymentMethod;
import com.insightdevelop.interline.domain.booking.Segment;
import com.insightdevelop.interline.domain.offer.FlightOffer;
import com.insightdevelop.interline.domain.offer.Itinerary;
import com.insightdevelop.interline.domain.offer.OfferSearchCriteria;
import com.insightdevelop.interline.domain.offer.OfferSegment;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import com.insightdevelop.interline.domain.shared.Page;
import com.insightdevelop.interline.infrastructure.rest.dto.AirportDto;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingActionDto;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingDto;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingPageDto;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingSummaryDto;
import com.insightdevelop.interline.infrastructure.rest.dto.ContactDto;
import com.insightdevelop.interline.infrastructure.rest.dto.CreateBookingRequestDto;
import com.insightdevelop.interline.infrastructure.rest.dto.FareDto;
import com.insightdevelop.interline.infrastructure.rest.dto.FlightOfferDto;
import com.insightdevelop.interline.infrastructure.rest.dto.FlightSegmentDto;
import com.insightdevelop.interline.infrastructure.rest.dto.InterlineEligibilityDto;
import com.insightdevelop.interline.infrastructure.rest.dto.ItineraryDto;
import com.insightdevelop.interline.infrastructure.rest.dto.MilesAmountDto;
import com.insightdevelop.interline.infrastructure.rest.dto.MoneyDto;
import com.insightdevelop.interline.infrastructure.rest.dto.OfferSearchRequestDto;
import com.insightdevelop.interline.infrastructure.rest.dto.OfferSearchResponseDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PassengerDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentMethodDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentRequestDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.SegmentDto;
import com.insightdevelop.interline.infrastructure.rest.dto.SegmentStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.StatusChangeDto;
import com.insightdevelop.interline.infrastructure.rest.dto.TicketDto;
import com.insightdevelop.interline.infrastructure.rest.problem.InvalidRequestException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Traducción entre los DTO generados desde el contrato y el modelo de dominio. Vive en el
 * adaptador REST: ni el dominio ni la aplicación conocen los DTO.
 */
final class RestMapper {

    /** Horas locales siempre con segundos, como en los ejemplos del contrato. */
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private RestMapper() {
    }

    // ------------------------------------------------------------------ peticiones -> dominio

    static OfferSearchCriteria toCriteria(OfferSearchRequestDto dto, AirlineCode tenant) {
        var pax = dto.getPassengers();
        AirlineCode validating = Optional.ofNullable(dto.getValidatingAirline()).map(AirlineCode::of).orElse(tenant);
        return new OfferSearchCriteria(AirportCode.of(dto.getOrigin()), AirportCode.of(dto.getDestination()),
                dto.getDepartureDate(), dto.getReturnDate(),
                new PassengerCounts(pax.getAdults(), orZero(pax.getChildren()), orZero(pax.getInfants())),
                validating);
    }

    static CreateBookingCommand toCommand(CreateBookingRequestDto dto, AirlineCode tenant) {
        var contact = new Contact(dto.getContact().getEmail(), dto.getContact().getPhone());
        var passengers = dto.getPassengers().stream().map(p -> new CreateBookingCommand.PassengerDraft(
                p.getRef(), PassengerType.valueOf(p.getType().name()), p.getFirstName().strip(),
                p.getLastName().strip(), p.getDateOfBirth(), p.getAssociatedAdultRef())).toList();
        return new CreateBookingCommand(dto.getOfferId(), contact, passengers, tenant);
    }

    /**
     * La forma del pago según el método es parte del contrato: si no corresponde es un 400
     * (petición mal formada), no un 422.
     */
    static PaymentCommand toCommand(PaymentRequestDto dto) {
        PaymentMethod method = PaymentMethod.valueOf(dto.getMethod().name());
        boolean hasCash = dto.getCash() != null;
        boolean hasMiles = dto.getMiles() != null;
        if (method.usesCash() != hasCash) {
            throw new InvalidRequestException("cash", method.usesCash()
                    ? "es obligatorio para el método " + method : "no se admite con el método " + method);
        }
        if (method.usesMiles() != hasMiles) {
            throw new InvalidRequestException("miles", method.usesMiles()
                    ? "es obligatorio para el método " + method : "no se admite con el método " + method);
        }
        Money cash = hasCash ? Money.of(dto.getCash().getAmount(), dto.getCash().getCurrency()) : null;
        Miles miles = hasMiles ? Miles.of(dto.getMiles().getAmount()) : Miles.ZERO;
        String member = hasMiles ? dto.getMiles().getMemberNumber() : null;
        return new PaymentCommand(method, cash, miles, member);
    }

    // ------------------------------------------------------------------ dominio -> respuestas

    static AirportDto toDto(Airport a) {
        return new AirportDto(a.code().value(), a.name(), a.city(), a.countryCode());
    }

    static OfferSearchResponseDto toDto(List<OfferSearchResult> results) {
        return new OfferSearchResponseDto(results.size(), results.stream().map(RestMapper::toDto).toList());
    }

    private static FlightOfferDto toDto(OfferSearchResult result) {
        FlightOffer offer = result.offer();
        return new FlightOfferDto(offer.offerId(), offer.validatingCarrier().value(), toDto(result.interline()),
                offer.itineraries().stream().map(RestMapper::toDto).toList(), toDto(offer.fare()),
                utc(offer.expiresAt()));
    }

    private static InterlineEligibilityDto toDto(InterlineEligibility e) {
        return new InterlineEligibilityDto(e.singleTicketEligible(), codes(e.operatingCarriers()),
                codes(e.missingAgreements()));
    }

    private static ItineraryDto toDto(Itinerary i) {
        return new ItineraryDto(ItineraryDto.DirectionEnum.fromValue(i.direction().name()), i.duration().toString(),
                i.segments().stream().map(RestMapper::toDto).toList());
    }

    private static FlightSegmentDto toDto(OfferSegment s) {
        return new FlightSegmentDto(s.operatingCarrier().value(), s.flightNumber().value(), s.origin().value(),
                s.destination().value(), local(s.departure()), local(s.arrival()));
    }

    private static FareDto toDto(Fare fare) {
        return new FareDto(toDto(fare.total()), fare.milesEquivalent().value());
    }

    private static MoneyDto toDto(Money money) {
        return new MoneyDto(money.amount(), money.currency().getCurrencyCode());
    }

    static BookingDto toDto(Booking b) {
        var dto = new BookingDto(
                b.locator().value(),
                BookingStatusDto.valueOf(b.status().name()),
                b.validatingCarrier().value(),
                b.passengers().stream().map(p -> new PassengerDto(p.id().value(),
                                com.insightdevelop.interline.infrastructure.rest.dto.PassengerTypeDto.valueOf(p.type().name()),
                                p.firstName(), p.lastName(), p.dateOfBirth())
                        .associatedAdultId(p.associatedAdult().map(PassengerId::value).orElse(null))).toList(),
                b.segments().stream().map(RestMapper::toDto).toList(),
                b.payments().stream().map(RestMapper::toDto).toList(),
                b.tickets().stream().map(t -> new TicketDto(t.number().value(), t.passengerId().value(),
                        utc(t.issuedAt()))).toList(),
                b.statusHistory().stream().map(c -> new StatusChangeDto(BookingStatusDto.valueOf(c.to().name()),
                                utc(c.at()))
                        .from(c.previous().map(s -> BookingStatusDto.valueOf(s.name())).orElse(null))
                        .reason(c.reason())).toList(),
                b.availableActions().stream().map(a -> BookingActionDto.valueOf(a.name())).toList(),
                utc(b.createdAt()),
                utc(b.updatedAt()));
        b.fare().ifPresent(f -> dto.fare(toDto(f)));
        dto.contact(new ContactDto(b.contact().email()).phone(b.contact().phone()));
        return dto;
    }

    private static SegmentDto toDto(Segment s) {
        return new SegmentDto(s.operatingCarrier().value(), s.flightNumber().value(), s.origin().value(),
                s.destination().value(), local(s.departure()), local(s.arrival()), s.id().value(),
                SegmentStatusDto.valueOf(s.status().name()));
    }

    private static PaymentDto toDto(Payment p) {
        var dto = new PaymentDto(p.id().value(), PaymentMethodDto.valueOf(p.method().name()),
                PaymentStatusDto.valueOf(p.status().name()), utc(p.createdAt()));
        p.cash().ifPresent(c -> dto.cash(toDto(c)));
        if (p.miles().isPositive()) {
            dto.miles(new MilesAmountDto(p.miles().value(), mask(p.memberNumber().orElse(""))));
        }
        return dto;
    }

    static BookingPageDto toDto(Page<Booking> page) {
        return new BookingPageDto(page.items().stream().map(RestMapper::toSummary).toList(), page.page(),
                page.size(), page.totalElements(), page.totalPages());
    }

    private static BookingSummaryDto toSummary(Booking b) {
        return new BookingSummaryDto(b.locator().value(), BookingStatusDto.valueOf(b.status().name()),
                b.validatingCarrier().value(), b.segments().getFirst().origin().value(),
                b.segments().getLast().destination().value(), local(b.firstDeparture()), b.passengers().size(),
                utc(b.createdAt()));
    }

    // ------------------------------------------------------------------ utilidades

    /** Número de socio enmascarado: solo los 4 últimos dígitos. */
    static String mask(String memberNumber) {
        int visible = Math.min(4, memberNumber.length());
        return "*".repeat(memberNumber.length() - visible) + memberNumber.substring(memberNumber.length() - visible);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String local(LocalDateTime dateTime) {
        return dateTime.format(LOCAL_DATE_TIME);
    }

    private static List<String> codes(Collection<AirlineCode> codes) {
        return codes.stream().map(AirlineCode::value).toList();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
