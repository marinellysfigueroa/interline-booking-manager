import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra el stack real: backend Quarkus en modo dev (Dev Services levanta PostgreSQL y
 * WireMock con el contrato estilo Amadeus) y el frontend con `ng serve` (proxy /api → :8080).
 * En local reutiliza los servidores si ya están arrancados.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4200',
    locale: 'es-ES',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
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
