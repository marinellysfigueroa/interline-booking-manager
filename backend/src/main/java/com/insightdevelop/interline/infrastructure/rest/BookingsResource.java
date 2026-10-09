package com.insightdevelop.interline.infrastructure.rest;

import com.insightdevelop.interline.application.booking.AuthorizePaymentUseCase;
import com.insightdevelop.interline.application.booking.BookingQueries;
import com.insightdevelop.interline.application.booking.CancelBookingUseCase;
import com.insightdevelop.interline.application.booking.CreateBookingUseCase;
import com.insightdevelop.interline.application.booking.TicketingSaga;
import com.insightdevelop.interline.domain.airline.AirlineCode;
import com.insightdevelop.interline.domain.booking.Booking;
import com.insightdevelop.interline.domain.booking.BookingLocator;
import com.insightdevelop.interline.domain.booking.BookingStatus;
import com.insightdevelop.interline.domain.port.BookingRepository;
import com.insightdevelop.interline.domain.shared.PageRequest;
import com.insightdevelop.interline.infrastructure.rest.api.BookingsApi;
import com.insightdevelop.interline.infrastructure.rest.dto.BookingStatusDto;
import com.insightdevelop.interline.infrastructure.rest.dto.CancelBookingRequestDto;
import com.insightdevelop.interline.infrastructure.rest.dto.CreateBookingRequestDto;
import com.insightdevelop.interline.infrastructure.rest.dto.PaymentRequestDto;
import com.insightdevelop.interline.infrastructure.rest.idempotency.IdempotencyService;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BookingsResource implements BookingsApi {

    private static final String BASE_PATH = "/api/v1/bookings";

    private final CreateBookingUseCase createBooking;
    private final AuthorizePaymentUseCase authorizePayment;
    private final TicketingSaga ticketingSaga;
    private final CancelBookingUseCase cancelBooking;
    private final BookingQueries queries;
    private final IdempotencyService idempotency;

    BookingsResource(CreateBookingUseCase createBooking, AuthorizePaymentUseCase authorizePayment,
            TicketingSaga ticketingSaga, CancelBookingUseCase cancelBooking, BookingQueries queries,
            IdempotencyService idempotency) {
        this.createBooking = createBooking;
        this.authorizePayment = authorizePayment;
        this.ticketingSaga = ticketingSaga;
        this.cancelBooking = cancelBooking;
        this.queries = queries;
        this.idempotency = idempotency;
    }

    @Override
    public Response createBooking(String idempotencyKey, CreateBookingRequestDto request, String xCorrelationID,
            String xTenant) {
        return idempotency.execute("POST " + BASE_PATH, idempotencyKey, request, BASE_PATH, () -> {
            Booking booking = createBooking.create(RestMapper.toCommand(request, tenant(xTenant)));
            return Response.created(location(booking.locator())).entity(RestMapper.toDto(booking)).build();
        });
    }

    @Override
    public Response authorizePayment(String idempotencyKey, String locator, PaymentRequestDto request,
            String xCorrelationID, String xTenant) {
        BookingLocator bookingLocator = BookingLocator.of(locator);
        String path = BASE_PATH + "/" + bookingLocator + "/payments";
        return idempotency.execute("POST " + path, idempotencyKey, request, path, () -> {
            Booking booking = authorizePayment.authorize(bookingLocator, RestMapper.toCommand(request), idempotencyKey);
            return Response.created(location(bookingLocator)).entity(RestMapper.toDto(booking)).build();
        });
    }

    @Override
    public Response getBooking(String locator, String xCorrelationID, String xTenant) {
        return Response.ok(RestMapper.toDto(queries.get(BookingLocator.of(locator)))).build();
    }

    @Override
    public Response listBookings(String xCorrelationID, String xTenant, List<BookingStatusDto> status, Integer page,
            Integer size) {
        Set<BookingStatus> statuses = status == null ? Set.of()
                : status.stream().map(s -> BookingStatus.valueOf(s.name())).collect(Collectors.toSet());
        var query = new BookingRepository.Query(statuses, tenant(xTenant));
        return Response.ok(RestMapper.toDto(queries.list(query, new PageRequest(page, size)))).build();
    }

    @Override
    public Response issueTicket(String locator, String xCorrelationID, String xTenant) {
        return Response.ok(RestMapper.toDto(ticketingSaga.issue(BookingLocator.of(locator)))).build();
    }

    @Override
    public Response cancelBooking(String locator, String xCorrelationID, String xTenant,
            CancelBookingRequestDto request) {
        String reason = request == null ? null : request.getReason();
        return Response.ok(RestMapper.toDto(cancelBooking.cancel(BookingLocator.of(locator), reason))).build();
    }

    private static URI location(BookingLocator locator) {
        return URI.create(BASE_PATH + "/" + locator);
    }

    private static AirlineCode tenant(String xTenant) {
        return xTenant == null ? null : AirlineCode.of(xTenant);
    }
}
