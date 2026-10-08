import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeEs from '@angular/common/locales/es';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import {
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { routes } from './app.routes';
import { RuntimeConfigService } from './core/config/runtime-config';
import {
  correlationIdInterceptor,
  errorInterceptor,
  idempotencyKeyInterceptor,
  tenantInterceptor,
} from './core/http/interceptors';
import { BrandTitleStrategy } from './core/theming/brand-title.strategy';

registerLocaleData(localeEs);

/**
 * Configuración de la aplicación (sin NgModules: solo standalone + providers funcionales).
 * Angular 22 es zoneless por defecto: la detección de cambios la disparan los signals y los
 * eventos, y todos los componentes usan OnPush.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    // Orden: correlación → tenant → idempotencia → errores (el último ve la respuesta primero)
    provideHttpClient(
      withFetch(),
      withInterceptors([
        correlationIdInterceptor,
        tenantInterceptor,
        idempotencyKeyInterceptor,
        errorInterceptor,
      ]),
    ),
    // La configuración white-label se carga antes de arrancar (≈ un ApplicationRunner que bloquea)
    provideAppInitializer(() => inject(RuntimeConfigService).load()),
    { provide: TitleStrategy, useClass: BrandTitleStrategy },
    { provide: LOCALE_ID, useValue: 'es' },
  ],
};
