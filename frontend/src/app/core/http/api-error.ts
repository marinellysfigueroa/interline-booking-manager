import { HttpErrorResponse } from '@angular/common/http';
import type { BookingStatus, Problem } from '../api/api.types';

/** Error de la API normalizado a partir de un Problem Details (RFC 7807). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly title: string,
    readonly detail: string,
    readonly fieldErrors: { field: string; message: string }[] = [],
    readonly correlationId?: string,
    readonly bookingStatus?: BookingStatus,
    readonly locator?: string,
  ) {
    super(detail || title);
  }

  static from(error: HttpErrorResponse, correlationId?: string): ApiError {
    if (error.status === 0) {
      return new ApiError(
        0,
        'NETWORK_ERROR',
        'Sin conexión',
        'No se pudo contactar con el servidor.',
      );
    }
    const problem = isProblem(error.error) ? error.error : null;
    return new ApiError(
      error.status,
      problem?.code ?? `HTTP_${error.status}`,
      problem?.title ?? error.statusText ?? 'Error',
      problem?.detail ?? 'Se produjo un error inesperado.',
      problem?.errors ?? [],
      problem?.correlationId ?? correlationId,
      problem?.bookingStatus,
      problem?.locator,
    );
  }
}

function isProblem(body: unknown): body is Problem {
  return typeof body === 'object' && body !== null && 'status' in body && 'title' in body;
}
