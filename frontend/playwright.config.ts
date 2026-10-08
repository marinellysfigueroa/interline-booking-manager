import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra el stack real. Dos modos:
 * - Por defecto: arranca backend Quarkus en modo dev (Dev Services levanta PostgreSQL y WireMock)
 *   y el frontend con `ng serve` (proxy /api → :8080); reutiliza los que ya estén corriendo.
 * - `E2E_BASE_URL=http://localhost:4200 npm run e2e`: prueba un stack ya desplegado
 *   (docker compose, Cloud Run) sin arrancar nada.
 */
const externalBaseUrl = process.env['E2E_BASE_URL'];

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: externalBaseUrl ?? 'http://localhost:4200',
    locale: 'es-ES',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: externalBaseUrl
    ? undefined
    : [
        {
          command: './mvnw -q quarkus:dev -Dquarkus.console.enabled=false',
          cwd: '../backend',
          url: 'http://localhost:8080/q/health/ready',
          timeout: 300_000,
          reuseExistingServer: !process.env['CI'],
          stdout: 'ignore',
        },
        {
          command: 'npm start -- --port 4200',
          url: 'http://localhost:4200',
          timeout: 180_000,
          reuseExistingServer: !process.env['CI'],
        },
      ],
});
