import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from '../config/runtime-config';
import { IDEMPOTENCY_KEY } from '../http/http-context';
import type {
  Airport,
  Booking,
  BookingPage,
  BookingStatus,
  CreateBookingRequest,
  OfferSearchRequest,
  OfferSearchResponse,
  PaymentRequest,
} from './api.types';

/**
 * Cliente HTTP tipado de la API. Las cabeceras transversales (correlación, tenant,
 * idempotencia) y el manejo de errores los añaden los interceptors, no este servicio.
 */
@Injectable({ providedIn: 'root' })
export class InterlineApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);

  private url(path: string): string {
    return `${this.config.value.apiBaseUrl}/api/v1${path}`;
  }

  searchAirports(query: string, limit = 8): Observable<Airport[]> {
    return this.http.get<Airport[]>(this.url('/airports'), {
      params: new HttpParams().set('query', query).set('limit', limit),
    });
  }

  searchOffers(request: OfferSearchRequest): Observable<OfferSearchResponse> {
    return this.http.post<OfferSearchResponse>(this.url('/offers/search'), request);
  }

  /** @param idempotencyKey misma clave en cada reintento del mismo intento lógico */
  createBooking(request: CreateBookingRequest, idempotencyKey: string): Observable<Booking> {
    return this.http.post<Booking>(this.url('/bookings'), request, {
      context: new HttpContext().set(IDEMPOTENCY_KEY, idempotencyKey),
    });
  }

  authorizePayment(
    locator: string,
    request: PaymentRequest,
    idempotencyKey: string,
  ): Observable<Booking> {
    return this.http.post<Booking>(this.url(`/bookings/${locator}/payments`), request, {
      context: new HttpContext().set(IDEMPOTENCY_KEY, idempotencyKey),
    });
  }

  issueTicket(locator: string): Observable<Booking> {
    return this.http.post<Booking>(this.url(`/bookings/${locator}/ticket`), null);
  }

  cancelBooking(locator: string, reason?: string): Observable<Booking> {
    return this.http.post<Booking>(
      this.url(`/bookings/${locator}/cancel`),
      reason ? { reason } : {},
    );
  }

  getBooking(locator: string): Observable<Booking> {
    return this.http.get<Booking>(this.url(`/bookings/${locator}`));
  }

  listBookings(filter: {
    statuses: BookingStatus[];
    page: number;
    size: number;
  }): Observable<BookingPage> {
    let params = new HttpParams().set('page', filter.page).set('size', filter.size);
    for (const status of filter.statuses) {
      params = params.append('status', status);
    }
    return this.http.get<BookingPage>(this.url('/bookings'), { params });
  }
}
