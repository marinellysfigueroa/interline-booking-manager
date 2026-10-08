import { HttpContextToken } from '@angular/common/http';

/** Clave de idempotencia elegida por quien hace la petición (estable entre reintentos). */
export const IDEMPOTENCY_KEY = new HttpContextToken<string | null>(() => null);

/** Desactiva el aviso global de error (la pantalla lo muestra a su manera). */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);
