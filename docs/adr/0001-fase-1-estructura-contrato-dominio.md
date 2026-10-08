# ADR 0001 — Fase 1: estructura del monorepo, contrato OpenAPI y modelo de dominio

- **Estado:** propuesto (pendiente de revisión)
- **Fecha:** 2026-10-08

## Contexto

Primera fase de Interline Booking Manager: fijar la estructura del repositorio, el
contrato HTTP (contract-first) y el modelo de dominio antes de escribir persistencia,
casos de uso o adaptadores.

## Decisiones

### D1. Monorepo con el contrato en la raíz

```
api/        contrato OpenAPI (fuente única de verdad) + config del linter
backend/    Quarkus 3.40 LTS, Java 21, Maven
frontend/   Angular (standalone + signals)
docs/adr/   decisiones de arquitectura
```

El contrato vive en `api/` y no dentro de `backend/` porque lo consumen los dos
proyectos: el backend genera sus interfaces JAX-RS y el frontend generará sus tipos.
Más adelante se añadirán `wiremock/` (fase 3), `infra/` (fase 6) y `.github/` (fase 6).

**Versión de Quarkus:** se mantiene la 3.40.1 que ya traía el scaffold. Es la LTS
publicada el 30-09-2026 (soporte hasta el 30-09-2027). La 3.33 LTS anterior seguirá
soportada hasta marzo de 2027, pero empezar con la más reciente evita una migración a
mitad del proyecto.

### D2. Arquitectura hexagonal por paquetes, verificada con ArchUnit

`com.insightdevelop.interline.{domain, application, infrastructure}` en un solo módulo
Maven. Se descartó un multi-módulo (`domain`, `application`, `infrastructure` como
artefactos separados) porque complica el modo dev de Quarkus y el build nativo sin
aportar mucho en un proyecto de este tamaño. La separación se protege con
`HexagonalArchitectureTest` (ArchUnit), que falla el build si:

- el dominio importa Jakarta, Quarkus, Hibernate, Jackson, MicroProfile o Vert.x;
- el dominio depende de `application`/`infrastructure`, o `application` de `infrastructure`;
- hay ciclos entre los paquetes del dominio.

El dominio no lleva ni siquiera anotaciones CDI. Los servicios de dominio (p. ej.
`InterlineTicketingPolicy`) se publicarán como beans con productores CDI en la fase 2.
En Spring sería lo mismo que declararlos con `@Bean` en vez de `@Service`.

### D3. Contract-first con OpenAPI 3.0.3 y generación de interfaces

- El contrato se escribe a mano en `api/openapi.yaml`, se valida con
  `@redocly/cli lint` (las reglas que exigen descripciones, respuestas 4xx y ejemplos
  válidos están como `error`) y se publica **tal cual** en `/openapi` y Swagger UI
  (`mp.openapi.scan.disable=true`): lo que se sirve es exactamente lo revisado.
- `openapi-generator` (`jaxrs-spec`, `interfaceOnly`) genera en cada build una
  interfaz JAX-RS por tag y los DTO con anotaciones de Bean Validation. Los recursos de
  la fase 2 las implementarán, así que si el contrato y el código divergen, el build
  falla.
- Se usa **3.0.3** y no 3.1 porque el soporte de 3.1 en openapi-generator sigue siendo
  parcial.

### D4. Semántica de errores e idempotencia

- Problem Details (RFC 7807) con las extensiones `code` (estable, legible por
  máquina), `correlationId`, `bookingStatus` y `errors[]`.
- **400** = la petición no cumple el contrato (formato, campos obligatorios, falta
  `Idempotency-Key`, la forma del pago no corresponde al método). **404** = recurso
  inexistente. **409** = conflicto con el estado actual (transición inválida, petición
  idempotente en curso). **422** = la petición cumple el contrato pero viola una regla de
  negocio (infantes, interline, pago insuficiente, oferta expirada, segmento UC).
- En el dominio, `DomainException` es una clase **sellada** con un subtipo por familia
  (`BusinessRuleViolation` → 422, `InvalidStateTransition` → 409, `ResourceNotFound` →
  404, `ExternalServiceUnavailable` → 503). El mapper de la fase 2 usará un `switch`
  exhaustivo sobre ella.
- `Idempotency-Key` es obligatorio en `POST /bookings` y `POST /bookings/{locator}/payments`
  (según draft-ietf-httpapi-idempotency-key-header): misma clave y mismo cuerpo
  devuelven la respuesta original con `Idempotency-Replayed: true`; misma clave con otro
  cuerpo da 422; una petición aún en curso da 409. `ticket` y `cancel` son idempotentes
  por estado y no exigen la clave.
- Se reserva por `offerId`: el servidor guarda las ofertas buscadas (`OfferStore`) y
  nunca confía en precios enviados por el cliente.

### D5. Máquina de estados como `enum` con `switch` exhaustivo

`BookingStatus.allowedTransitions()` declara las transiciones en un `switch` sobre el
propio enum. Se consideró una `sealed interface` con un record por estado. Habría
permitido que cada estado tuviera datos propios (p. ej. `Ticketed(tickets)`), pero
complica la persistencia, la serialización y las consultas por estado. El `switch`
exhaustivo ya obliga a declarar las transiciones de cualquier estado nuevo, y la
jerarquía sellada se usa donde sí aporta: las excepciones (D4).

`TICKETED` es terminal: cancelar una reserva emitida implicaría anulación o reembolso,
fuera del alcance → 409.

### D6. Agregado `Booking` e invariantes

- `Booking` es la raíz. `Segment` y `Payment` son entidades internas cuyos métodos de
  cambio de estado son *package-private*, así que solo la raíz puede modificarlas.
- Todas las operaciones que usará la saga son **idempotentes** (confirmar o cancelar un
  segmento, capturar o liberar un pago, `issueTickets`, `fail`, `cancel`): repetir un
  paso ya aplicado no cambia nada.
- **Invariante de salida:** `fail` y `cancel` exigen que no queden segmentos activos ni
  pagos reteniendo fondos (`RESOURCES_STILL_HELD`). La saga debe compensar primero y
  cerrar después. Por eso, en la saga de emisión, la captura del pago es el último paso
  antes de emitir.
- La regla interline se comprueba **antes** que el resto de precondiciones de emisión:
  si falla, la reserva no cambia de estado (422).
- Pasajeros: edad por tipo a la fecha del primer vuelo, ≥ 1 ADT, ≤ 9 asientos,
  INF ≤ ADT y cada INF asociado a un ADT distinto de la misma reserva.
- Pago mixto: cubre la tarifa si `cash/total + miles/milesEquivalent ≥ 1`, evaluado
  con multiplicación cruzada para evitar redondeos.
- Las operaciones reciben `Instant now` en lugar de leer el reloj, lo que da pruebas
  deterministas. En aplicación se inyectará un `Clock`.
- Horas de vuelo **locales** (`LocalDateTime`), como en los GDS. Las duraciones las
  calcula el proveedor.
- Localizador de 6 caracteres sin 0/1/I/O (32^6 combinaciones). Las colisiones se
  resuelven reintentando contra un índice único.

### D7. Puertos definidos en el dominio

`FlightOffersProvider`, `OfferStore`, `BookingRepository`, `AirlineRepository`,
`AirportRepository`, `SegmentInventoryGateway`, `PaymentGateway` y `LoyaltyGateway`
están en `domain.port`. `TicketNumberGenerator` vive junto al agregado (`domain.booking`)
porque `Booking.issueTickets` lo necesita: ponerlo en `domain.port` crearía un ciclo
de paquetes. Todos los gateways deben ser idempotentes.

## Consecuencias

- **+** El contrato se revisa y se valida antes de implementar, y el build impide que la
  implementación diverja de él.
- **+** El dominio se prueba con JUnit puro, en milisegundos y sin Docker.
- **−** Duplicamos tipos: DTO generados ↔ modelo de dominio ↔ entidades JPA. La
  conversión se hará con mappers explícitos en infraestructura (sin MapStruct por ahora).
- **−** Los DTO generados no son records. Es aceptable porque solo viven en el borde.

## Pendiente para la fase 2

- Reconstitución del agregado desde persistencia (`Booking.restore`/snapshot) y
  bloqueo optimista (`@Version`).
- Mapeo de `DomainException` y `ConstraintViolationException` a Problem Details.
- Almacén de claves de idempotencia (tabla con hash del cuerpo y respuesta guardada).
