import { Injectable, computed, effect, inject, signal } from '@angular/core';
import {
  Observable,
  catchError,
  concatMap,
  finalize,
  map,
  of,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import type {
  Booking,
  Contact,
  FlightOffer,
  OfferSearchRequest,
  PassengerInput,
  PaymentRequest,
} from '../../core/api/api.types';
import { InterlineApi } from '../../core/api/interline-api';
import { ApiError } from '../../core/http/api-error';

export type FlowStep = 'search' | 'offers' | 'passengers' | 'payment' | 'confirmation';
export const FLOW_STEPS: readonly FlowStep[] = [
  'search',
  'offers',
  'passengers',
  'payment',
  'confirmation',
];

export type SearchCriteria = Required<
  Pick<OfferSearchRequest, 'origin' | 'destination' | 'departureDate'>
> & {
  returnDate?: string;
  passengers: { adults: number; children: number; infants: number };
};

/** Clave de idempotencia ligada al cuerpo exacto que protege. */
interface IdempotencySlot {
  key: string;
  body: string;
}

export interface BookingFlowState {
  version: 1;
  criteria: SearchCriteria | null;
  offers: FlightOffer[];
  selectedOfferId: string | null;
  passengers: PassengerInput[];
  contact: Contact | null;
  booking: Booking | null;
  idempotency: { create?: IdempotencySlot; payment?: IdempotencySlot };
}

export const BOOKING_FLOW_STORAGE_KEY = 'ibm.booking-flow';

const INITIAL_STATE: BookingFlowState = {
  version: 1,
  criteria: null,
  offers: [],
  selectedOfferId: null,
  passengers: [],
  contact: null,
  booking: null,
  idempotency: {},
};

const FINAL_STATUSES = new Set(['TICKETED', 'FAILED', 'CANCELLED']);

/**
 * Signal store del flujo de reserva (uno por feature, sin librerías).
 *
 * - Estado en un único `signal` privado; la lectura pública son `computed` de solo lectura.
 * - Un `effect` persiste el estado en sessionStorage: recargar la página no pierde el progreso
 *   (y cada pestaña lleva su propia reserva).
 * - Las operaciones HTTP son `Observable` (RxJS) que actualizan el estado con `tap`; el
 *   componente decide cómo combinarlas (p. ej. `exhaustMap` en el botón de pagar).
 * - `allowedSteps` es la regla que usan los guards para impedir saltar pasos.
 *
 * Se provee en la ruta `/booking` (no en root): vive mientras dure el flujo.
 */
@Injectable()
export class BookingFlowStore {
  private readonly api = inject(InterlineApi);
  private readonly state = signal<BookingFlowState>(hydrate());

  private readonly busy = signal(false);
  private readonly lastError = signal<ApiError | null>(null);

  readonly criteria = computed(() => this.state().criteria);
  readonly offers = computed(() => this.state().offers);
  readonly passengers = computed(() => this.state().passengers);
  readonly contact = computed(() => this.state().contact);
  readonly booking = computed(() => this.state().booking);
  readonly loading = this.busy.asReadonly();
  readonly error = this.lastError.asReadonly();

  readonly selectedOffer = computed(
    () => this.offers().find((o) => o.offerId === this.state().selectedOfferId) ?? null,
  );
  /** Fecha del primer vuelo: referencia para validar la edad de cada pasajero. */
  readonly firstDepartureDate = computed(
    () => this.selectedOffer()?.itineraries[0].segments[0].departureAt.slice(0, 10) ?? null,
  );

  /** Pasos a los que se puede navegar con el estado actual. */
  readonly allowedSteps = computed<readonly FlowStep[]>(() => {
    const booking = this.booking();
    if (booking) {
      // Con la reserva creada ya no se vuelve atrás: o se paga, o se ve el resultado.
      return FINAL_STATUSES.has(booking.status) ? ['confirmation'] : ['payment'];
    }
    const steps: FlowStep[] = ['search'];
    if (this.criteria() && this.offers().length > 0) steps.push('offers');
    if (this.selectedOffer()) steps.push('passengers');
    return steps;
  });

  /** Paso más avanzado permitido: destino de las redirecciones de los guards. */
  readonly furthestStep = computed(() => this.allowedSteps()[this.allowedSteps().length - 1]);

  constructor() {
    effect(() => persist(this.state()));
  }

  canEnter(step: FlowStep): boolean {
    return this.allowedSteps().includes(step);
  }

  // ------------------------------------------------------------------ acciones

  search(criteria: SearchCriteria): Observable<FlightOffer[]> {
    return this.track(
      this.api.searchOffers(criteria).pipe(
        map((response) => response.offers),
        tap((offers) => this.patch({ ...INITIAL_STATE, criteria, offers })),
      ),
    );
  }

  selectOffer(offerId: string): void {
    this.patch({ selectedOfferId: offerId, passengers: [], contact: null, idempotency: {} });
  }

  createBooking(passengers: PassengerInput[], contact: Contact): Observable<Booking> {
    const offer = this.selectedOffer();
    if (!offer) {
      return throwError(() => new Error('No hay oferta seleccionada'));
    }
    this.patch({ passengers, contact });
    const request = { offerId: offer.offerId, passengers, contact };
    const key = this.keyFor('create', request);
    return this.track(
      this.api.createBooking(request, key).pipe(tap((booking) => this.patch({ booking }))),
    );
  }

  /**
   * Autoriza el pago (si aún no lo está) y emite el ticket. Si la saga compensa, la API
   * responde 422 con `bookingStatus: FAILED`: se recarga la reserva para mostrar el resultado.
   */
  payAndIssue(request: PaymentRequest): Observable<Booking> {
    const booking = this.booking();
    if (!booking) {
      return throwError(() => new Error('No hay reserva'));
    }
    const authorize$ =
      booking.status === 'PAYMENT_AUTHORIZED'
        ? of(booking)
        : this.api
            .authorizePayment(booking.locator, request, this.keyFor('payment', request))
            .pipe(tap((authorized) => this.patch({ booking: authorized })));
    return this.track(
      authorize$.pipe(
        concatMap((authorized) => this.api.issueTicket(authorized.locator)),
        tap((ticketed) => this.patch({ booking: ticketed })),
        catchError((error: unknown) =>
          error instanceof ApiError && error.bookingStatus === 'FAILED'
            ? this.api.getBooking(booking.locator).pipe(
                tap((failed) => this.patch({ booking: failed })),
                switchMap(() => throwError(() => error)),
              )
            : throwError(() => error),
        ),
      ),
    );
  }

  /** Empieza una reserva nueva conservando los criterios de búsqueda. */
  reset(): void {
    this.state.set({ ...INITIAL_STATE, criteria: this.criteria() });
  }

  // ------------------------------------------------------------------ internos

  /**
   * Misma clave mientras el cuerpo no cambie (reintentos seguros); clave nueva si el usuario
   * modifica los datos, para no recibir un 422 IDEMPOTENCY_KEY_REUSED.
   */
  private keyFor(slot: keyof BookingFlowState['idempotency'], body: unknown): string {
    const serialized = JSON.stringify(body);
    const current = this.state().idempotency[slot];
    if (current?.body === serialized) {
      return current.key;
    }
    const key = crypto.randomUUID();
    this.patch({ idempotency: { ...this.state().idempotency, [slot]: { key, body: serialized } } });
    return key;
  }

  private track<T>(source: Observable<T>): Observable<T> {
    return of(null).pipe(
      tap(() => {
        this.busy.set(true);
        this.lastError.set(null);
      }),
      concatMap(() => source),
      catchError((error: unknown) => {
        this.lastError.set(error instanceof ApiError ? error : null);
        return throwError(() => error);
      }),
      finalize(() => this.busy.set(false)),
    );
  }

  private patch(partial: Partial<BookingFlowState>): void {
    this.state.update((state) => ({ ...state, ...partial }));
  }
}

function hydrate(): BookingFlowState {
  try {
    const raw = sessionStorage.getItem(BOOKING_FLOW_STORAGE_KEY);
    const parsed = raw ? (JSON.parse(raw) as BookingFlowState) : null;
    return parsed?.version === 1 ? { ...INITIAL_STATE, ...parsed } : INITIAL_STATE;
  } catch {
    return INITIAL_STATE;
  }
}

function persist(state: BookingFlowState): void {
  try {
    sessionStorage.setItem(BOOKING_FLOW_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // almacenamiento lleno o bloqueado: el flujo sigue funcionando en memoria
  }
}
