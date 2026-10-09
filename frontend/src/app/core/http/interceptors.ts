import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationStore } from '../notifications/notification.store';
import { ThemeService } from '../theming/theme.service';
import { ApiError } from './api-error';
import { IDEMPOTENCY_KEY, SILENT_ERRORS } from './http-context';

const CORRELATION_HEADER = 'X-Correlation-ID';

/** Interceptor funcional (equivale a un ClientHttpRequestInterceptor de Spring en el navegador). */
export const correlationIdInterceptor: HttpInterceptorFn = (req, next) =>
  req.headers.has(CORRELATION_HEADER)
    ? next(req)
    : next(req.clone({ setHeaders: { [CORRELATION_HEADER]: crypto.randomUUID() } }));

/** Envía el código de la aerolínea white-label activa. */
export const tenantInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApi(req.url)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-Tenant': inject(ThemeService).tenant() } }));
};

const IDEMPOTENT_POSTS = [/\/api\/v1\/bookings$/, /\/api\/v1\/bookings\/[A-Z0-9]{6}\/payments$/];

/**
 * Añade `Idempotency-Key` a los POST de reserva y pago. Si la petición trae su propia clave en
 * el contexto (la del intento lógico, guardada en el store) se usa esa; si no, una nueva.
 */
export const idempotencyKeyInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method !== 'POST' || !IDEMPOTENT_POSTS.some((pattern) => pattern.test(req.url))) {
    return next(req);
  }
  const key = req.context.get(IDEMPOTENCY_KEY) ?? crypto.randomUUID();
  return next(req.clone({ setHeaders: { 'Idempotency-Key': key } }));
};

/**
 * Normaliza cualquier error HTTP a {@link ApiError} (Problem Details) y lo notifica de forma
 * centralizada, salvo que la petición pida silencio con SILENT_ERRORS.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationStore);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const apiError = ApiError.from(error, req.headers.get(CORRELATION_HEADER) ?? undefined);
      if (!req.context.get(SILENT_ERRORS)) {
        notifications.error(apiError.title, apiError.detail, apiError.correlationId);
      }
      return throwError(() => apiError);
    }),
  );
};

function isApi(url: string): boolean {
  return url.includes('/api/');
}
