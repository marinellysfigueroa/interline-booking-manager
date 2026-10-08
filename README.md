# Interline Booking Manager

Gestión de reservas con itinerarios de varias aerolíneas emitidos en un solo ticket
(**interline**), inspirada en plataformas white-label de aerolíneas.

Quarkus 3.40 LTS (Java 21) + Angular + PostgreSQL, desplegable en GCP (Cloud Run).

> 🚧 En construcción por fases. Fase actual: **3 — proveedor de vuelos estilo Amadeus,
> WireMock y resiliencia**. Al terminar se completará este README con la arquitectura, el
> despliegue y la sección "Quarkus para desarrolladores Spring".

## Estructura

```
api/          Contrato OpenAPI (fuente única de verdad)
backend/      API Quarkus — arquitectura hexagonal (domain / application / infrastructure)
frontend/     SPA Angular
wiremock/     Contrato estilo Amadeus (stubs WireMock: mappings + __files)
docs/adr/     Architecture Decision Records
```

## Comandos

Requisitos: Java 21 y Docker. En dev y test, Dev Services levanta automáticamente
PostgreSQL y WireMock (con los stubs de `/wiremock` como proveedor de vuelos).

```bash
# Validar el contrato
cd api && npx @redocly/cli@2.60.0 lint openapi.yaml

# Todas las pruebas: dominio, arquitectura y @QuarkusTest contra PostgreSQL (Dev Services)
cd backend && ./mvnw test

# Modo desarrollo con recarga en caliente (PostgreSQL en Docker automático)
cd backend && ./mvnw quarkus:dev
# → http://localhost:8080/swagger-ui · /q/health · /q/metrics · Dev UI en /q/dev-ui
```

Flujo de ejemplo con `curl` (ofertas desde WireMock; inventario, pagos y millas simulados):

```bash
OFFER=$(curl -s localhost:8080/api/v1/offers/search -H 'Content-Type: application/json' \
  -d '{"origin":"BOG","destination":"FCO","departureDate":"2026-12-15","passengers":{"adults":1}}' \
  | jq -r '.offers[0].offerId')

LOC=$(curl -s localhost:8080/api/v1/bookings -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"offerId\":\"$OFFER\",\"contact\":{\"email\":\"ana@example.com\"},
       \"passengers\":[{\"ref\":\"P1\",\"type\":\"ADT\",\"firstName\":\"Ana\",\"lastName\":\"Pérez\",\"dateOfBirth\":\"1990-04-12\"}]}" \
  | jq -r .locator)

AMOUNT=$(curl -s localhost:8080/api/v1/bookings/$LOC | jq .fare.total.amount)
curl -s localhost:8080/api/v1/bookings/$LOC/payments -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -d "{\"method\":\"CASH\",\"cash\":{\"amount\":$AMOUNT,\"currency\":\"USD\"}}" | jq .status
curl -s -X POST localhost:8080/api/v1/bookings/$LOC/ticket | jq '{status, tickets}'
```

Proveedor de ofertas: `FLIGHT_OFFERS_PROVIDER=amadeus-style` (por defecto; requiere
`AMADEUS_BASE_URL`, `AMADEUS_CLIENT_ID` y `AMADEUS_CLIENT_SECRET` en prod) o `simulated`
(horario fijo, sin red).

Para ver la compensación de la saga en local:
`./mvnw quarkus:dev -Dapp.simulated.inventory.never-confirm-carriers=IB` y emitir una reserva BOG→FCO.

## Decisiones

- [ADR 0001 — Estructura, contrato OpenAPI y modelo de dominio](docs/adr/0001-fase-1-estructura-contrato-dominio.md)
- [ADR 0002 — Persistencia, casos de uso, saga e idempotencia](docs/adr/0002-fase-2-persistencia-casos-de-uso-saga.md)
- [ADR 0003 — Proveedor estilo Amadeus, WireMock y resiliencia](docs/adr/0003-fase-3-proveedor-estilo-amadeus.md)
