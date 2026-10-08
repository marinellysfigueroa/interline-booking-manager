import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../core/http/api-error';
import { IDEMPOTENCY_KEY } from '../../core/http/http-context';
import { errorInterceptor } from '../../core/http/interceptors';
import { OFFER, booking } from '../../testing/fixtures';
import { provideTestRuntimeConfig } from '../../testing/test-config';
import { BOOKING_FLOW_STORAGE_KEY, BookingFlowStore, SearchCriteria } from './booking-flow.store';

const CRITERIA: SearchCriteria = {
  origin: 'BOG',
  destination: 'FCO',
  departureDate: '2026-11-20',
  passengers: { adults: 1, children: 0, infants: 0 },
};
const PASSENGERS = [
  {
    ref: 'A1',
    type: 'ADT' as const,
    firstName: 'Ana',
    lastName: 'Pérez',
    dateOfBirth: '1990-04-12',
  },
];
const CONTACT = { email: 'ana@example.com' };

describe('BookingFlowStore', () => {
  let http: HttpTestingController;

  function createStore(): BookingFlowStore {
    return TestBed.inject(BookingFlowStore);
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        BookingFlowStore,
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideTestRuntimeConfig(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function searchAndSelect(store: BookingFlowStore): Promise<void> {
    const result = firstValueFrom(store.search(CRITERIA));
    http.expectOne('/api/v1/offers/search').flush({ count: 1, offers: [OFFER] });
    await result;
    store.selectOffer(OFFER.offerId);
  }

  async function createHeld(store: BookingFlowStore): Promise<void> {
    await searchAndSelect(store);
    const created = firstValueFrom(store.createBooking(PASSENGERS, CONTACT));
    http.expectOne('/api/v1/bookings').flush(booking('HELD'));
    await created;
  }

  it('starts at the search step only', () => {
    const store = createStore();
    expect(store.allowedSteps()).toEqual(['search']);
    expect(store.furthestStep()).toBe('search');
    expect(store.canEnter('payment')).toBe(false);
  });

  it('search stores the offers and unlocks the offers step', async () => {
    const store = createStore();
    const result = firstValueFrom(store.search(CRITERIA));
    const req = http.expectOne('/api/v1/offers/search');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(CRITERIA);
    expect(store.loading()).toBe(true);
    req.flush({ count: 1, offers: [OFFER] });

    expect(await result).toEqual([OFFER]);
    expect(store.loading()).toBe(false);
    expect(store.allowedSteps()).toEqual(['search', 'offers']);
  });

  it('selecting an offer unlocks passengers and exposes the first departure date', async () => {
    const store = createStore();
    await searchAndSelect(store);

    expect(store.selectedOffer()?.offerId).toBe(OFFER.offerId);
    expect(store.firstDepartureDate()).toBe('2026-11-20');
    expect(store.furthestStep()).toBe('passengers');
  });

  it('once the booking exists only the payment step is allowed', async () => {
    const store = createStore();
    await createHeld(store);

    expect(store.allowedSteps()).toEqual(['payment']);
    expect(store.canEnter('passengers')).toBe(false);
  });

  it('reuses the idempotency key when retrying the same booking and renews it when data changes', async () => {
    const store = createStore();
    await searchAndSelect(store);

    const first = firstValueFrom(store.createBooking(PASSENGERS, CONTACT)).catch((e) => e);
    const req1 = http.expectOne('/api/v1/bookings');
    req1.flush(null, { status: 0, statusText: 'Network error' });
    await first;

    const retry = firstValueFrom(store.createBooking(PASSENGERS, CONTACT)).catch((e) => e);
    const req2 = http.expectOne('/api/v1/bookings');
    expect(req2.request.context.get(IDEMPOTENCY_KEY)).toBe(
      req1.request.context.get(IDEMPOTENCY_KEY),
    );
    req2.flush(null, { status: 0, statusText: 'Network error' });
    await retry;

    const changed = firstValueFrom(
      store.createBooking([{ ...PASSENGERS[0], firstName: 'Anna' }], CONTACT),
    );
    const req3 = http.expectOne('/api/v1/bookings');
    expect(req3.request.context.get(IDEMPOTENCY_KEY)).not.toBe(
      req1.request.context.get(IDEMPOTENCY_KEY),
    );
    req3.flush(booking('HELD'));
    await changed;
  });

  it('pays and issues: authorize then ticket, ending at confirmation', async () => {
    const store = createStore();
    await createHeld(store);

    const done = firstValueFrom(
      store.payAndIssue({ method: 'CASH', cash: { amount: 1120, currency: 'USD' } }),
    );
    const pay = http.expectOne('/api/v1/bookings/K7Q2MX/payments');
    expect(pay.request.context.get(IDEMPOTENCY_KEY)).toBeTruthy();
    pay.flush(booking('PAYMENT_AUTHORIZED'));
    http.expectOne('/api/v1/bookings/K7Q2MX/ticket').flush(booking('TICKETED'));

    expect((await done).status).toBe('TICKETED');
    expect(store.allowedSteps()).toEqual(['confirmation']);
  });

  it('when the saga compensates it reloads the booking as FAILED', async () => {
    const store = createStore();
    await createHeld(store);

    const done = firstValueFrom(
      store.payAndIssue({ method: 'CASH', cash: { amount: 1120, currency: 'USD' } }),
    ).catch((e) => e);
    http.expectOne('/api/v1/bookings/K7Q2MX/payments').flush(booking('PAYMENT_AUTHORIZED'));
    http.expectOne('/api/v1/bookings/K7Q2MX/ticket').flush(
      {
        type: 'x',
        title: 'Segmento no confirmado',
        status: 422,
        code: 'SEGMENT_NOT_CONFIRMED',
        bookingStatus: 'FAILED',
        locator: 'K7Q2MX',
        detail: 'IB3234 siguió UC',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    http.expectOne('/api/v1/bookings/K7Q2MX').flush(booking('FAILED'));

    const error = await done;
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('SEGMENT_NOT_CONFIRMED');
    expect(store.booking()?.status).toBe('FAILED');
    expect(store.allowedSteps()).toEqual(['confirmation']);
  });

  it('skips the authorization when the payment was already authorized', async () => {
    const store = createStore();
    await searchAndSelect(store);
    const created = firstValueFrom(store.createBooking(PASSENGERS, CONTACT));
    http.expectOne('/api/v1/bookings').flush(booking('PAYMENT_AUTHORIZED'));
    await created;

    const done = firstValueFrom(
      store.payAndIssue({ method: 'CASH', cash: { amount: 1120, currency: 'USD' } }),
    );
    http.expectNone('/api/v1/bookings/K7Q2MX/payments');
    http.expectOne('/api/v1/bookings/K7Q2MX/ticket').flush(booking('TICKETED'));
    await done;
  });

  it('persists progress in sessionStorage and restores it after a reload', async () => {
    const store = createStore();
    await searchAndSelect(store);
    TestBed.tick(); // ejecuta el effect de persistencia

    expect(JSON.parse(sessionStorage.getItem(BOOKING_FLOW_STORAGE_KEY)!).selectedOfferId).toBe(
      OFFER.offerId,
    );

    // "Recarga": un injector nuevo crea otra instancia del store que hidrata desde sessionStorage
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        BookingFlowStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTestRuntimeConfig(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const restored = TestBed.inject(BookingFlowStore);
    expect(restored.selectedOffer()?.offerId).toBe(OFFER.offerId);
    expect(restored.furthestStep()).toBe('passengers');
  });

  it('reset starts a new booking keeping the search criteria', async () => {
    const store = createStore();
    await createHeld(store);

    store.reset();

    expect(store.booking()).toBeNull();
    expect(store.criteria()).toEqual(CRITERIA);
    expect(store.allowedSteps()).toEqual(['search']);
  });
});
