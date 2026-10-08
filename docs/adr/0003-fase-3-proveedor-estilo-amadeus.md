# ADR 0003 — Fase 3: proveedor de vuelos estilo Amadeus, WireMock y resiliencia

- **Estado:** propuesto (pendiente de revisión)
- **Fecha:** 2026-10-08

## Contexto

El portal self-service de Amadeus se desactivó en julio de 2026, así que no hay API
pública. Necesitamos integrar un proveedor de ofertas con un contrato realista, probarlo
de forma determinista y hacerlo resiliente, de modo que conectar una API real en el
futuro solo implique escribir un adaptador nuevo.

## Decisiones

### D1. Contrato "estilo Amadeus" definido por nuestros stubs

- El contrato vive en `/wiremock` (`mappings/` + `__files/`), en la raíz del monorepo.
  Lo usan el Dev Service en dev/test y, en la fase 5, el contenedor de docker-compose.
- **Endpoints que definimos** (y ninguno más):
  - `POST /v1/security/oauth2/token`: form `grant_type=client_credentials`,
    `client_id`, `client_secret`. Devuelve `access_token`, `token_type`, `expires_in`,
    `state`.
  - `GET /v2/shopping/flight-offers` con `originLocationCode`,
    `destinationLocationCode`, `departureDate`, `returnDate` (opcional), `adults`,
    `children`, `infants`, `currencyCode` y `max`. Devuelve `meta.count` y `data[]` con
    `id`, `itineraries[].duration`, `itineraries[].segments[]`
    (`departure/arrival.iataCode`, `at`, `carrierCode`, `number`,
    `operating.carrierCode`), `price.currency/total/grandTotal`,
    `validatingAirlineCodes` y `lastTicketingDate`.
- **Formas asumidas, marcadas en el código** (`AmadeusFlightOffersResponse`,
  `AmadeusTokenResponse`):
  - `at` es hora local sin offset.
  - `grandTotal` es el total de todos los pasajeros, en texto decimal.
  - `operating` puede faltar si coincide con la comercializadora.
  - `state` vale `approved`.
  - Errores con forma `{"errors":[{status, code, title, detail}]}`.
- Los stubs usan **plantillas de respuesta** de WireMock: las fechas salen de la petición
  (`{{request.query.departureDate}}`, con `+1 día` para los vuelos nocturnos) y el precio
  se calcula por pasajero con el helper `math` (tarifa × asientos + 10 % por infante). Así
  las pruebas pueden buscar con fechas relativas (`hoy + 30`).
- **Rutas:**
  - BOG-MAD (solo ida e ida y vuelta);
  - MAD-FCO (IB y AZ);
  - BOG-FCO (AV + IB, elegible para ticket único);
  - FCO-BOG;
  - LIM-MIA (LA y AA, solo ida e ida y vuelta);
  - MIA-LIM;
  - LIM-MAD (validada por LA con un tramo de AV: **no elegible**);
  - MIA-MAD.

  Una ruta sin vuelos devuelve `data: []`, y sin token válido se responde 401.
  Las plantillas se validaron contra WireMock 3.13.2 en Docker.

### D2. Adaptador hexagonal y capa anticorrupción

- `AmadeusStyleFlightOffersProvider` implementa el puerto de dominio
  `FlightOffersProvider` y se registra con `@Identifier("amadeus-style")`.
  `app.flight-offers.provider` (variable `FLIGHT_OFFERS_PROVIDER`) elige entre
  `amadeus-style` (por defecto) y `simulated` (offline) **en tiempo de ejecución**.
- Hay dos interfaces MicroProfile REST Client, `AmadeusAuthClient` y
  `AmadeusFlightOffersClient`, equivalentes a `@HttpExchange` u OpenFeign en Spring.
- `AmadeusOfferMapper` traduce el contrato al dominio:
  - Genera un `offerId` propio, porque los del proveedor solo son únicos dentro de una
    búsqueda.
  - Usa la aerolínea **operadora**, que es la que cuenta para la regla interline.
  - Redondea los importes a la escala de la moneda.
  - Calcula `milesEquivalent` con `app.amadeus-style.miles-per-currency-unit`, porque el
    contrato no trae precio en millas.
  - Si una oferta no se puede interpretar, la **descarta** con un aviso en vez de
    romper toda la búsqueda.
- `AmadeusResponseExceptionMapper` clasifica los errores HTTP en una jerarquía sellada:
  `Transient` (5xx, 429, 408), `Rejected` (otros 4xx) y `Unauthorized` (401).
- **Token OAuth2:** `AmadeusTokenProvider` lo cachea hasta `expires_in − 60 s`. Ante un
  401 lo invalida y repite la llamada una vez. Usa `ReentrantLock` y no `synchronized`,
  porque en Java 21 un `synchronized` alrededor de I/O ancla el hilo virtual a su
  portador.
- `X-Correlation-ID` se propaga al proveedor con un `ClientRequestFilter`.
- Las credenciales solo llegan por entorno (`AMADEUS_CLIENT_ID` /
  `AMADEUS_CLIENT_SECRET`; Secret Manager en la fase 6). Los valores de dev/test son los
  que esperan los stubs y no son secretos. Si faltan, la app arranca igual (útil con
  `simulated`) y la búsqueda responde 503 indicando qué variable falta.

### D3. Resiliencia con MicroProfile Fault Tolerance

```
@Fallback( @Retry + @ExponentialBackoff( @CircuitBreaker( @Timeout( llamada ) ) ) )
```

| Política | Valor (prod) | Decisión |
| --- | --- | --- |
| `@Timeout` | 3 s por intento | Junto con los timeouts del REST Client (2 s de conexión, 5 s de lectura) |
| `@CircuitBreaker` | ventana de 6, 50 % de fallos, abierto 10 s | `skipOn` 4xx y 401: no son caídas del proveedor. Nombre `flight-offers-provider` (permite consultarlo y resetearlo) |
| `@Retry` | 2 reintentos, 200 ms ×2 con jitter | `abortOn` 4xx, 401, circuito abierto y configuración incompleta |
| `@Fallback` | — | Convierte el fallo técnico en `ExternalServiceUnavailableException` → **503 + `Retry-After`** |

- El retry va **por fuera** del circuit breaker, así que cada intento cuenta como una
  llamada en la ventana. Con el circuito abierto, el reintento se aborta y falla al
  instante.
- Los valores se pueden ajustar sin recompilar con
  `quarkus.fault-tolerance."<clase>/search".*`. El perfil test los acorta (timeout
  800 ms, reintentos de 20 ms, ventana de 4).
- Se descartó un fallback "degradado" (servir la última respuesta cacheada) porque los
  precios caducan rápido y reservar sobre un precio viejo es peor que un 503 claro.
  Queda como opción con TTL corto si el negocio lo pide.
- La readiness **no** depende del proveedor: un tercero caído no debe sacar la instancia
  del balanceador. El estado del circuito se ve en las métricas de Fault Tolerance
  (Micrometer/Prometheus).

### D4. Hilos virtuales; cuándo convendría Mutiny

`OffersResource.searchOffers` lleva `@RunOnVirtualThread`. El código sigue siendo
imperativo (REST Client síncrono, JDBC), pero mientras espera la red el hilo portador
queda libre. Comprobado en `quarkus:dev`: el log muestra `quarkus-virtual-thread-0` e
`isVirtual() = true`.

**Mutiny** (`Uni`/`Multi` en el event loop) convendría para *componer* I/O:

- consultar varios proveedores en paralelo y combinar resultados, o quedarse con el
  primero que responda;
- aplicar backpressure o hacer streaming (SSE) de ofertas a medida que llegan;
- cuando toda la cadena ya es reactiva (Hibernate Reactive, cliente PG reactivo).

Para un flujo secuencial (token → búsqueda → guardar → evaluar), los hilos virtuales
dan la misma escalabilidad con código más simple. Es el mismo dilema que Spring MVC con
hilos virtuales frente a WebFlux.

### D5. WireMock como Dev Service (Quarkiverse `quarkus-wiremock` 1.8.1)

Se usa la extensión, compilada contra Quarkus 3.40.1, en lugar de un
`QuarkusTestResourceLifecycleManager` propio:

- arranca WireMock en dev y test con los stubs de `../wiremock`;
- publica el puerto en `${quarkus.wiremock.devservices.port}`;
- `@ConnectWireMock` inyecta un cliente para verificar peticiones y registrar stubs de
  fallos solo durante una prueba.

Va con `scope provided`, así que no entra en el artefacto de producción.

## Pruebas

| Prueba | Qué demuestra |
| --- | --- |
| Toda la suite anterior (`BookingFlowTest`, saga, idempotencia…) | Ahora pasa por el adaptador estilo Amadeus contra WireMock, no por el simulador |
| `AmadeusStyleAdapterTest` | Mapeo completo (operadoras, fechas locales, precio por pasajero, millas, TTL), ida y vuelta, parámetros enviados, `Authorization`, correlation ID propagado, renovación de token tras 401 (una sola llamada al token), descarte de ofertas inválidas |
| `FlightProviderResilienceTest` | Reintento hasta el éxito (503 → 500 → 200); timeout → 3 intentos → 503 + `Retry-After`; 4xx sin reintentos y sin abrir el circuito; el circuito se abre tras 4 fallos y deja de llamar al proveedor; recuperación tras el reset |
| `SimulatedFlightOffersProviderTest` | El proveedor offline, en JUnit puro |

También se verificó en perfil **prod**, contra contenedores de PostgreSQL y WireMock:

- con credenciales por entorno, la búsqueda funciona;
- sin credenciales, responde 503 indicando la variable que falta;
- con `FLIGHT_OFFERS_PROVIDER=simulated`, arranca sin credenciales.

## Consecuencias

- **+** El contrato del proveedor es explícito, versionado y ejecutable. Las pruebas no
  dependen de terceros.
- **+** Cambiar a un proveedor real implica un adaptador nuevo con su `@Identifier`;
  dominio, aplicación y API no cambian.
- **−** Los stubs no garantizan que un proveedor real se comporte igual. Al integrar uno
  habrá que añadir pruebas de contrato contra su sandbox.
- **−** El circuito es global para el método `search`. Si en el futuro hay varios
  proveedores, cada adaptador tendrá su propio circuito con nombre.
