import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Colores de marca que se aplican como CSS custom properties. */
export interface ThemeColors {
  primary: string;
  primaryContrast: string;
  accent: string;
  surface: string;
  background: string;
  text: string;
  muted: string;
}

/** Tema white-label de una aerolínea (tenant). */
export interface TenantTheme {
  /** Código IATA de la aerolínea; viaja en el header X-Tenant. */
  tenant: string;
  name: string;
  tagline: string;
  logo: string;
  colors: ThemeColors;
}

/**
 * Configuración cargada en runtime desde `/config/runtime-config.json`. Al no compilarse en el
 * bundle, la misma imagen sirve a cualquier aerolínea y entorno: basta con montar otro JSON.
 */
export interface RuntimeConfig {
  /** Prefijo de la API; vacío = mismo origen (proxy de desarrollo o nginx). */
  apiBaseUrl: string;
  defaultTenant: string;
  themes: TenantTheme[];
}

@Injectable({ providedIn: 'root' })
export class RuntimeConfigService {
  private readonly http = inject(HttpClient);
  private readonly config = signal<RuntimeConfig | null>(null);

  /** Se ejecuta antes de arrancar la app (provideAppInitializer). */
  async load(): Promise<void> {
    this.config.set(
      await firstValueFrom(this.http.get<RuntimeConfig>('config/runtime-config.json')),
    );
  }

  get value(): RuntimeConfig {
    const config = this.config();
    if (!config) {
      throw new Error('La configuración runtime aún no se ha cargado');
    }
    return config;
  }
}
