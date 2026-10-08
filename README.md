# Interline Booking Manager

Gestión de reservas con itinerarios de varias aerolíneas emitidos en un solo ticket
(**interline**), inspirada en plataformas white-label de aerolíneas.

Quarkus 3.40 LTS (Java 21) + Angular + PostgreSQL, desplegable en GCP (Cloud Run).

> 🚧 En construcción por fases. Fase actual: **2 — persistencia, casos de uso, saga e
> idempotencia**. Al terminar se completará este README con la arquitectura, el
> despliegue y la sección "Quarkus para desarrolladores Spring".

## Estructura

```
api/          Contrato OpenAPI (fuente única de verdad)
backend/      API Quarkus — arquitectura hexagonal (domain / application / infrastructure)
frontend/     SPA Angular
docs/adr/     Architecture Decision Records
```

## Comandos

Requisitos: Java 21 y Docker (Dev Services levanta PostgreSQL automáticamente en dev y test).

```bash
# Validar el contrato
cd api && npx @redocly/cli@2.60.0 lint openapi.yaml

# Todas las pruebas: dominio, arquitectura y @QuarkusTest contra PostgreSQL (Dev Services)
cd backend && ./mvnw test

# Modo desarrollo con recarga en caliente (PostgreSQL en Docker automático)
cd backend && ./mvnw quarkus:dev
# → http://localhost:8080/swagger-ui · /q/health · /q/metrics · Dev UI en /q/dev-ui
```

Flujo de ejemplo con `curl` (proveedor y sistemas externos simulados):

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

Para ver la compensación de la saga en local:
`./mvnw quarkus:dev -Dapp.simulated.inventory.never-confirm-carriers=IB` y emitir una reserva BOG→FCO.

## Decisiones

- [ADR 0001 — Estructura, contrato OpenAPI y modelo de dominio](docs/adr/0001-fase-1-estructura-contrato-dominio.md)
- [ADR 0002 — Persistencia, casos de uso, saga e idempotencia](docs/adr/0002-fase-2-persistencia-casos-de-uso-saga.md)
