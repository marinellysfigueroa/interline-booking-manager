import { RuntimeConfig, RuntimeConfigService } from '../core/config/runtime-config';

export const TEST_CONFIG: RuntimeConfig = {
  apiBaseUrl: '',
  defaultTenant: 'AV',
  themes: [
    {
      tenant: 'AV',
      name: 'Aerolíneas Cóndor',
      tagline: '',
      logo: '',
      colors: {
        primary: '#000',
        primaryContrast: '#fff',
        accent: '#f00',
        surface: '#fff',
        background: '#fff',
        text: '#000',
        muted: '#666',
      },
    },
    {
      tenant: 'LA',
      name: 'Pacífico Air',
      tagline: '',
      logo: '',
      colors: {
        primary: '#111',
        primaryContrast: '#fff',
        accent: '#0f0',
        surface: '#fff',
        background: '#fff',
        text: '#000',
        muted: '#666',
      },
    },
  ],
};

/** Sustituye la carga HTTP de la configuración runtime en las pruebas. */
export const provideTestRuntimeConfig = () => ({
  provide: RuntimeConfigService,
  useValue: { value: TEST_CONFIG, load: () => Promise.resolve() },
});
