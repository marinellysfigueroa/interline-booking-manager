import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { provideTestRuntimeConfig } from '../../testing/test-config';
import { NotificationStore } from '../notifications/notification.store';
import { ThemeService } from '../theming/theme.service';
import { ApiError } from './api-error';
import { IDEMPOTENCY_KEY, SILENT_ERRORS } from './http-context';
import {
  correlationIdInterceptor,
  errorInterceptor,
  idempotencyKeyInterceptor,
  tenantInterceptor,
} from './interceptors';

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([
            correlationIdInterceptor,
            tenantInterceptor,
            idempotencyKeyInterceptor,
            errorInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
        provideTestRuntimeConfig(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('adds a correlation id and the active tenant to API calls', () => {
    TestBed.inject(ThemeService).select('LA');
    http.get('/api/v1/airports').subscribe();

    const req = backend.expectOne('/api/v1/airports');
    expect(req.request.headers.get('X-Correlation-ID')).toMatch(/^[0-9a-f-]{36}$/);
    expect(req.request.headers.get('X-Tenant')).toBe('LA');
    req.flush([]);
  });

  it('uses the idempotency key from the request context on booking POSTs', () => {
    http
      .post('/api/v1/bookings', {}, { context: new HttpContext().set(IDEMPOTENCY_KEY, 'key-123') })
      .subscribe();

    const req = backend.expectOne('/api/v1/bookings');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-123');
    req.flush({});
  });

  it('generates an idempotency key for payment POSTs without one, and never for other calls', () => {
    http.post('/api/v1/bookings/K7Q2MX/payments', {}).subscribe();
    http.post('/api/v1/bookings/K7Q2MX/ticket', null).subscribe();
    http.get('/api/v1/bookings').subscribe();

    const payment = backend.expectOne('/api/v1/bookings/K7Q2MX/payments');
    expect(payment.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(
      backend.expectOne('/api/v1/bookings/K7Q2MX/ticket').request.headers.has('Idempotency-Key'),
    ).toBe(false);
    expect(backend.expectOne('/api/v1/bookings').request.headers.has('Idempotency-Key')).toBe(
      false,
    );
    backend.match(() => true).forEach((r) => r.flush({}));
    payment.flush({});
  });

  it('turns Problem Details into ApiError and notifies once', async () => {
    const notifications = TestBed.inject(NotificationStore);
    const result = firstValueFrom(http.get('/api/v1/bookings/ZZZZZZ')).catch((e) => e);

    backend.expectOne('/api/v1/bookings/ZZZZZZ').flush(
      {
        type: 'x',
        title: 'Reserva no encontrada',
        status: 404,
        code: 'BOOKING_NOT_FOUND',
        detail: 'No existe',
        correlationId: 'c-1',
      },
      { status: 404, statusText: 'Not Found' },
    );

    const error = (await result) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, code: 'BOOKING_NOT_FOUND', correlationId: 'c-1' });
    expect(notifications.notifications()).toHaveLength(1);
  });

  it('does not notify when the request asks for silence', async () => {
    const notifications = TestBed.inject(NotificationStore);
    const result = firstValueFrom(
      http.get('/api/v1/x', { context: new HttpContext().set(SILENT_ERRORS, true) }),
    ).catch((e) => e);
    backend.expectOne('/api/v1/x').flush(null, { status: 0, statusText: 'offline' });

    expect(((await result) as ApiError).code).toBe('NETWORK_ERROR');
    expect(notifications.notifications()).toHaveLength(0);
  });
});
