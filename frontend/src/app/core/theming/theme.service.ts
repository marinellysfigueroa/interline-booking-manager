import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { RuntimeConfigService, TenantTheme, ThemeColors } from '../config/runtime-config';

const STORAGE_KEY = 'ibm.tenant';

/** CSS custom property → clave del tema. */
const CSS_VARIABLES: Record<string, keyof ThemeColors> = {
  '--brand-primary': 'primary',
  '--brand-primary-contrast': 'primaryContrast',
  '--brand-accent': 'accent',
  '--surface': 'surface',
  '--background': 'background',
  '--text': 'text',
  '--muted': 'muted',
};

/**
 * Theming white-label: el tenant activo sale de `?tenant=XX`, de la última elección
 * (localStorage) o del valor por defecto de la configuración runtime. Un `effect` aplica sus
 * colores como CSS custom properties sobre `<html>`, de modo que cambiar de aerolínea no
 * requiere recompilar ni recargar.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly config = inject(RuntimeConfigService);

  readonly themes = computed(() => this.config.value.themes);
  private readonly selected = signal(this.initialTenant());

  readonly theme = computed<TenantTheme>(
    () => this.themes().find((t) => t.tenant === this.selected()) ?? this.themes()[0],
  );
  readonly tenant = computed(() => this.theme().tenant);

  constructor() {
    effect(() => this.apply(this.theme()));
  }

  select(tenant: string): void {
    this.selected.set(tenant);
    try {
      localStorage.setItem(STORAGE_KEY, tenant);
    } catch {
      // almacenamiento no disponible (modo privado): el tema sigue funcionando en memoria
    }
  }

  private initialTenant(): string {
    const fromUrl = new URLSearchParams(this.document.location?.search ?? '').get('tenant');
    let stored: string | null = null;
    try {
      stored = localStorage.getItem(STORAGE_KEY);
    } catch {
      stored = null;
    }
    const known = new Set(this.config.value.themes.map((t) => t.tenant));
    return (
      [fromUrl?.toUpperCase(), stored].find((t) => !!t && known.has(t)) ??
      this.config.value.defaultTenant
    );
  }

  private apply(theme: TenantTheme): void {
    const root = this.document.documentElement;
    for (const [variable, key] of Object.entries(CSS_VARIABLES)) {
      root.style.setProperty(variable, theme.colors[key]);
    }
    root.dataset['tenant'] = theme.tenant;
  }
}
