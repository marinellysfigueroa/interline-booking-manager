# ADR 0002 — Fase 2: persistencia, casos de uso, saga e idempotencia

- **Estado:** aceptado
- **Fecha:** 2026-10-08

## Contexto

Con el contrato y el dominio aprobados (ADR 0001), esta fase implementa la persistencia,
los casos de uso, la saga de emisión con compensación, la API REST con Problem Details y
la idempotencia, todo con pruebas contra un PostgreSQL real.

## Decisiones

### D1. Persistencia: Panache en patrón Repository, entidades separadas del dominio

- Las entidades JPA (`infrastructure.persistence.entity`) son distintas del agregado. Un
  mapper explícito (`BookingEntityMapper`) traduce en ambos sentidos y, al actualizar,
  solo toca lo que puede cambiar: estados, pagos, tickets e historial (que solo crece).
- Cada adaptador (p. ej. `BookingRepositoryAdapter`) **compone** un `PanacheRepository`
  en lugar de heredarlo. Al principio se intentó heredar, y `OfferStore.findById`
  chocó con `PanacheRepositoryBase.findById`. Ese choque dejó ver el problema de fondo:
  heredar mezcla la API de Panache con el puerto de dominio.
- Esquema con Flyway (`V1__schema.sql`, `V2__reference_data.sql`); Hibernate solo **valida**
  (`schema-management.strategy=validate`). Columnas `VARCHAR` (no `CHAR`, que en
  PostgreSQL rellena con espacios y la validación de Hibernate rechaza).
- `booking` lleva columnas desnormalizadas (`origin`, `destination`, `first_departure`,
  `passenger_count`) para el listado, más `@Version` para el bloqueo optimista.
- Las ofertas se guardan como `JSONB` (viven 30 minutos y se leen enteras).
- Los números de ticket salen de una secuencia de PostgreSQL, así que son únicos aunque
  haya varias instancias.
- Los acuerdos interline de `V2` son **ficticios** (datos de demostración).

### D2. Transacciones cortas; llamadas externas siempre fuera de la transacción

Cada paso carga, modifica y guarda el agregado en su propia transacción
(`BookingTransactions` con `QuarkusTransaction.requiringNew()`, el equivalente a un
`TransactionTemplate` con `REQUIRES_NEW` en Spring). Las llamadas a inventario, pasarela
y programa de lealtad se hacen **entre** transacciones: no se retiene una conexión
mientras se reintenta contra un tercero.

Como la carga y el guardado ocurren en la misma transacción, la entidad sigue
gestionada y `@Version` detecta escrituras concurrentes al hacer flush. Se traduce a
`409 CONCURRENT_MODIFICATION` (lo cubre una prueba con dos hilos).

### D3. Saga de emisión orquestada, con el agregado como estado de la saga

`TicketingSaga`:

1. regla interline (si falla: 422 y la reserva no cambia);
2. confirmar los segmentos `UC` con reintentos y backoff exponencial
   (`app.saga.segment-confirmation.*`);
3. capturar el pago / redimir millas;
4. emitir (`TICKETED`).

Si un segmento sigue en `UC` (o pasa a `XX`), `BookingCompensator` cancela todos los
segmentos activos, libera la autorización y el hold de millas y pasa la reserva a `FAILED`.

- **No hay tabla de saga:** el estado de la saga son los estados de la reserva, de sus
  segmentos y de sus pagos. Como cada paso es idempotente (en el gateway y en el
  agregado), si el proceso se cae basta con repetir `POST /ticket`.
- La captura es el **último** paso antes de emitir. Antes de capturar todo es
  compensable; después no queda nada que pueda fallar por un tercero.
- El reintento de confirmación es un bucle explícito y no `@Retry`. Seguir en `UC` es
  una respuesta de negocio, no un fallo técnico. `@Retry`/`@CircuitBreaker` se reservan
  para fallos de red con proveedores (fase 3).
- La creación aplica el mismo patrón: si una operadora responde `XX` al vender, se
  compensa en el momento y la reserva queda `FAILED`.
- `BookingSagaFailedException` (capa de aplicación) envuelve la causa de dominio y aporta
  `locator` y `bookingStatus`, que viajan en el Problem Details. Por eso se añadió
  `locator` como extensión de `Problem` en el contrato (cambio aditivo).

### D4. Idempotencia persistente (`Idempotency-Key`)

Tabla `idempotency_record` con PK `(scope, key)`; el `scope` incluye la ruta (p. ej.
`POST /api/v1/bookings/K7Q2MX/payments`).

1. La clave se reserva en su propia transacción como `IN_PROGRESS`; la PK garantiza que
   solo una petición concurrente la gane (la otra recibe 409).
2. Se calcula el SHA-256 del cuerpo en JSON canónico (propiedades ordenadas). Si el
   hash es distinto → 422 `IDEMPOTENCY_KEY_REUSED`.
3. Se guarda la respuesta si es determinista (2xx y 4xx salvo 409) y se repite tal cual
   con `Idempotency-Replayed: true`. Si es 5xx o 409 se borra la clave para que el
   cliente pueda reintentar.
4. Un `IN_PROGRESS` de más de 2 minutos se considera abandonado y se retoma.

La clave también se propaga a la pasarela y al programa de lealtad (`key:cash`,
`key:miles`), así que un reintento tampoco duplica cargos aguas abajo.

### D5. Problem Details

- `ProblemResponses` concentra la traducción. Para `DomainException` usa un `switch`
  exhaustivo sobre la jerarquía sellada. Lo usan los `@ServerExceptionMapper` (≈
  `@RestControllerAdvice`) y el servicio de idempotencia, que necesita guardar la
  respuesta de error.
- En 409/422 sobre `/bookings/{locator}/...` se añade el `bookingStatus` actual.
- Ajustes sobre el comportamiento por defecto:
  - un `@QueryParam` inconvertible (un enum inválido) da **400**, no el 404 que manda
    JAX-RS;
  - un JSON mal formado o un campo ausente da 400 con el campo en `errors[]`;
  - un error no controlado da 500 sin detalles internos (pero con `correlationId`).
- 401/403 tienen mapeo explícito para cuando se active OIDC.

### D6. Adaptadores simulados y selección de proveedor en tiempo de ejecución

- Inventario, pasarela y programa de lealtad simulados en memoria. Son idempotentes y
  configurables (`app.simulated.inventory.*`) para reproducir en local los caminos de
  la saga: IB responde `UC` y confirma en la primera consulta. **Limitación:** el
  estado no se comparte entre instancias ni sobrevive a un reinicio.
- `SimulatedFlightOffersProvider`: horario fijo de demostración, para usar la app sin red.
- `FlightOffersProviderProducer` elige el adaptador con `@Identifier` +
  `app.flight-offers.provider`. Se descartó `@IfBuildProperty`, que es el equivalente
  directo de `@ConditionalOnProperty` pero se evalúa al compilar: con el productor, la
  misma imagen cambia de proveedor con una variable de entorno. En la fase 3 se añade
  `amadeus-style`.

### D7. Reglas de aplicación añadidas

- **`PASSENGER_MISMATCH` (422):** la oferta guarda para qué composición de pasajeros se
  tarificó, y la reserva debe coincidir con ella.
- **White-label:** la búsqueda filtra por la validadora pedida o, si no se indica,
  por `X-Tenant`. Una reserva con `X-Tenant` distinto de la validadora de la oferta
  devuelve `VALIDATING_AIRLINE_MISMATCH`.
- **Fecha de salida pasada:** devuelve 422 (`INVALID_ITINERARY`). La aplicación es la
  que tiene el `Clock`, así que lo comprueba ella y no el dominio.

### D8. Observabilidad y seguridad

- `CorrelationIdFilter` (`@ServerRequestFilter`, ≈ `OncePerRequestFilter`): lee o genera
  `X-Correlation-ID`, lo pone en el MDC y como atributo del span de OpenTelemetry, y lo
  devuelve en la respuesta. El MDC de Quarkus viaja en el contexto de Vert.x y sigue a
  la petición entre hilos (los logs de la saga muestran el mismo `cid`).
- Logs JSON en `prod` y texto con `[cid=...]` en dev/test. La readiness incluye la BD
  automáticamente; `/q/metrics` expone Prometheus.
- OIDC: `quarkus-oidc` instalado y configurado, pero **desactivado salvo
  `OIDC_ENABLED=true`**, siempre desactivado en dev/test y con Dev Services de Keycloak
  apagado. Cuando se active, `/api/*` exigirá un usuario autenticado. Se activará en
  Cloud Run en la fase 6, cuando haya un IdP.

## Pruebas

| Prueba | Qué demuestra |
| --- | --- |
| `BookingFlowTest` | Flujo feliz búsqueda → HELD → pago → TICKETED con línea de tiempo; regla interline (422 sin cambio de estado); 400/404/409/422 diferenciados; listado con filtros, tenant y paginación |
| `TicketingSagaCompensationTest` | `@InjectMock` del inventario y la pasarela: UC tras 3 intentos → cancela AV e IB, libera la autorización, `FAILED`; rechazo XX al crear |
| `IdempotencyTest` | Misma clave + mismo cuerpo → misma respuesta y una sola reserva; otro cuerpo → 422; los errores 4xx también se repiten; falta del header → 400; pago repetido → una autorización |
| `BookingRepositoryAdapterTest` | Ida y vuelta completa del agregado en PostgreSQL y bloqueo optimista con dos hilos |
| `AirportsResourceTest`, `OperationalEndpointsTest` | Autocompletado sin tildes, correlation ID, health, métricas, contrato publicado, errores HTTP genéricos |
| `HexagonalArchitectureTest` | Reglas de capas, DTO solo en el borde REST, entidades solo en infraestructura |

## Consecuencias

- **+** Las pruebas de integración corren contra PostgreSQL real (Dev Services), sin
  H2 ni configuración manual.
- **+** La saga se recupera repitiendo la petición, sin infraestructura extra.
- **−** No hay limpieza programada de `flight_offer` ni de `idempotency_record`
  caducados (pendiente: `@Scheduled` o un job de Cloud Scheduler).
- **−** Si el proceso muere a mitad de una compensación, nadie la retoma solo; hace
  falta repetir la petición. Una alternativa futura es un barrido periódico de reservas
  "atascadas" o un outbox.
