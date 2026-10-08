# ADR 0004 — Fase 4: frontend Angular

- **Estado:** aceptado
- **Fecha:** 2026-10-08

## Contexto

Necesitamos la SPA del flujo de reserva por pasos (buscar → oferta → pasajeros → pago →
confirmación), la gestión de reservas y el theming white-label por aerolínea, sobre la API
de las fases 1 a 3.

## Decisiones

### D1. Angular 22, standalone, zoneless y OnPush

- Se mantiene la versión del andamiaje (Angular 22.2). No hay NgModules: solo componentes
  standalone y providers funcionales (`provideRouter`, `provideHttpClient`,
  `provideAppInitializer`).
- La app es **zoneless** (el valor por defecto en Angular 22): la detección de cambios la
  disparan los signals y los eventos. Todos los componentes declaran
  `ChangeDetectionStrategy.OnPush`.
- TypeScript con `strict` y `strictTemplates`, que el andamiaje no traía activados.
- El CLI 22.2 exige Node ≥ 22.22.3 o ≥ 24.15. Se fija `24.15.0` en `.nvmrc` y en `engines`.

### D2. Contrato compartido: tipos generados desde `api/openapi.yaml`

`npm run api:types` genera `core/api/schema.d.ts` con openapi-typescript, y
`api.types.ts` define alias legibles (`Booking`, `FlightOffer`…). Así, si el contrato
cambia, el frontend deja de compilar en el sitio exacto.

openapi-typescript se ejecuta con `npx` en lugar de como dependencia, porque declara
como peer TypeScript 5 y el proyecto usa TypeScript 6. El archivo generado se versiona.

### D3. Estado: un signal store por feature, sin librerías

- `BookingFlowStore` y `ManageStore` siguen el mismo patrón:
  - un `signal` privado con el estado;
  - `computed` públicos de solo lectura;
  - métodos que devuelven `Observable` y actualizan el estado con `tap`.

  Se descartó NgRx Signal Store porque la petición era usar `signal`, `computed` y
  `effect` directamente, y con dos features no compensa una dependencia más.
- El store del flujo se **provee en la ruta `/booking`**, no en root: lo comparten los pasos
  y sus guards, y desaparece al salir del flujo.
- **Persistencia:** un `effect` guarda el estado en `sessionStorage` y el store se hidrata
  al construirse. Recargar no pierde el progreso, y cada pestaña lleva su propia reserva.
  `localStorage` solo guarda el tenant elegido.
- `toSignal`/`toObservable`:
  - el autocompletado expone los resultados con `toSignal`;
  - la gestión convierte los filtros y el tenant (signals) en un stream con
    `toObservable` + `switchMap`, que relanza la consulta y cancela la anterior;
  - el detalle reacciona al parámetro de ruta (input) con `toObservable`.

### D4. Navegación: rutas lazy por paso y guards

- Cada paso es una ruta `loadComponent`, y `/booking` y `/manage` son `loadChildren`.
- `stepGuard(step)` consulta `store.allowedSteps()`:
  - sin reserva creada, se avanza hasta el paso más lejano que permiten los datos;
  - con la reserva creada (HELD o PAYMENT_AUTHORIZED) solo se permite **pago**;
  - con la reserva en estado final, solo **confirmación**.

  Así no se puede volver atrás, cambiar la oferta y dejar una reserva huérfana. Las
  redirecciones van al paso más avanzado permitido. La e2e lo verifica en ambos sentidos.

### D5. Formularios y RxJS

- **Pasajeros:** un `FormArray` cuyas filas se generan con la composición para la que se
  tarificó la oferta (el backend devuelve `PASSENGER_MISMATCH` si no coincide).
  - `applyPassengerTypeValidators` asigna a cada fila validadores según su tipo: rango de
    edad en la fecha del **primer vuelo** (mismas reglas que el dominio Java), y adulto
    asociado obligatorio y habilitado solo para INF.
  - `infantRuleValidator` (validador de grupo sobre el FormArray) impide más infantes que
    adultos y dos infantes con el mismo adulto.
- **Autocompletado:** `debounceTime(300)` → `distinctUntilChanged` → `switchMap`. Es un
  combobox accesible (ARIA, navegación con teclado).
- **Pagar:** los clics alimentan un `Subject` y `exhaustMap` ignora los clics mientras hay
  un pago en vuelo. El pago y la emisión se encadenan con `concatMap`. Si la saga compensa
  (422 con `bookingStatus: FAILED`), el store recarga la reserva y se muestra el resultado.
  El botón de cancelar en el detalle sigue el mismo patrón.
- **Pago mixto:** `splitMixedPayment` trabaja en céntimos y redondea las millas hacia
  arriba, de modo que siempre se cumple `cash/total + miles/milesEquivalent ≥ 1`.

### D6. Interceptors funcionales

Se aplican en este orden:

1. `correlationIdInterceptor`: un `X-Correlation-ID` por petición.
2. `tenantInterceptor`: `X-Tenant` con la aerolínea activa.
3. `idempotencyKeyInterceptor`: `Idempotency-Key` solo en los POST de reserva y pago.
   Toma la clave del `HttpContext` cuando el store la aporta. El store **reutiliza la
   misma clave mientras el cuerpo no cambie** (reintento seguro) y genera otra si el
   usuario modifica los datos (evita el 422 `IDEMPOTENCY_KEY_REUSED`).
4. `errorInterceptor`: normaliza Problem Details a `ApiError` y notifica de forma
   centralizada con un toast que incluye el `correlationId`. Una petición puede pedir
   silencio con `SILENT_ERRORS`.

### D7. White-label en runtime

- `public/config/runtime-config.json` se carga con `provideAppInitializer` antes de
  arrancar. Contiene `apiBaseUrl`, `defaultTenant` y los temas (nombre, lema, logo y
  colores).
- No se compila en el bundle: la misma imagen sirve a cualquier aerolínea o entorno
  montando otro JSON (fases 5 y 6).
- `ThemeService` elige el tenant (`?tenant=`, luego la última elección, luego el valor por
  defecto) y un `effect` aplica los colores como **CSS custom properties** en `<html>`.
- `BrandTitleStrategy` compone el título "paso · aerolínea" y lo recalcula también al
  cambiar de tema.
- Hay dos temas de ejemplo con **marcas ficticias** y logos SVG propios: «Aerolíneas
  Cóndor» (tenant AV) y «Pacífico Air» (tenant LA). Se evitan nombres, colores y logos de
  aerolíneas reales.

### D8. `@defer`

- La línea de tiempo de la confirmación se carga `on idle`.
- La del detalle se carga `on viewport`.

No son críticas para la primera pintura y así salen del bundle inicial del paso.

## Pruebas

| Prueba | Qué cubre |
| --- | --- |
| `booking-flow.store.spec.ts` (10) | Pasos permitidos según el estado; búsqueda; selección; bloqueo tras crear la reserva; **misma Idempotency-Key en reintentos y nueva si cambia el cuerpo**; pago + emisión; saga compensada → FAILED; pago ya autorizado; persistencia e hidratación; reset |
| `passenger.validators.spec.ts` (19) | Edad por tipo con casos límite (cumpleaños el día del vuelo); validadores dinámicos; regla de infantes y su reevaluación |
| `payment-split.spec.ts` (9) | El reparto mixto siempre cubre la tarifa |
| `interceptors.spec.ts` (5) | Correlation ID, tenant, Idempotency-Key (contexto, generada, solo en los POST correctos), Problem → `ApiError`, silencio |
| `e2e/booking-happy-path.spec.ts` (3, Playwright) | Flujo completo BOG→FCO contra el stack real (Quarkus + PostgreSQL + WireMock): autocompletado, oferta interline, pasajeros, recarga en el pago sin perder progreso, emisión con ticket `134…`, guard tras confirmar, detalle con línea de tiempo y listado filtrado; cambio de tema en runtime (CSS vars, título, persistencia); no se puede saltar al pago |

## Consecuencias

- **+** El frontend comparte contrato y reglas con el backend: errores de pasajeros e
  idempotencia se resuelven en el cliente antes de llegar a la API.
- **+** Rebranding sin recompilar.
- **−** Las reglas de edad e infantes están duplicadas (Java y TypeScript). Se acepta por
  la experiencia de usuario; el backend sigue siendo la autoridad (422).
- **−** La e2e necesita Docker (Dev Services). En CI (fase 6) correrá con el runner de
  GitHub, que trae Docker.
- **Pendiente:** internacionalización (hoy solo español), modo oscuro y una página de
  error 404 propia.
