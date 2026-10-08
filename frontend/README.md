# Frontend — Interline Booking Manager

Angular 22 (standalone, signals, zoneless, OnPush). Ver el [README raíz](../README.md) y el
[ADR 0004](../docs/adr/0004-fase-4-frontend-angular.md).

```bash
nvm use                 # Node 24.15 (el CLI exige >= 22.22.3 o >= 24.15)
npm ci
npm start               # http://localhost:4200 (proxy /api → http://localhost:8080)
npm run test:ci         # pruebas unitarias (Vitest)
npm run e2e             # Playwright: arranca backend (quarkus:dev) y frontend si no están corriendo
npm run api:types       # regenera los tipos TypeScript desde ../api/openapi.yaml
```

Temas white-label: `public/config/runtime-config.json` (colores, logo, nombre por aerolínea).
Se elige con `?tenant=LA`, con el selector de la cabecera o por defecto (`defaultTenant`).
