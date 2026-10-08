# Interline Booking Manager

Gestión de reservas con itinerarios de varias aerolíneas emitidos en un solo ticket
(**interline**), inspirada en plataformas white-label de aerolíneas.

Quarkus 3.40 LTS (Java 21) + Angular + PostgreSQL, desplegable en GCP (Cloud Run).

> 🚧 En construcción por fases. Fase actual: **1 — estructura, contrato OpenAPI y
> modelo de dominio**. Al terminar se completará este README con la arquitectura, el
> despliegue y la sección "Quarkus para desarrolladores Spring".

## Estructura

```
api/          Contrato OpenAPI (fuente única de verdad)
backend/      API Quarkus — arquitectura hexagonal (domain / application / infrastructure)
frontend/     SPA Angular
docs/adr/     Architecture Decision Records
```

## Comandos (fase 1)

```bash
# Validar el contrato
cd api && npx @redocly/cli@2.60.0 lint openapi.yaml

# Pruebas de dominio y de arquitectura (no requieren Docker)
cd backend && ./mvnw test

# Ver el contrato publicado en Swagger UI
cd backend && ./mvnw package -DskipTests \
  && java -Dquarkus.datasource.devservices.enabled=false -jar target/quarkus-app/quarkus-run.jar
# → http://localhost:8080/swagger-ui  ·  http://localhost:8080/openapi
```

## Decisiones

- [ADR 0001 — Estructura, contrato OpenAPI y modelo de dominio](docs/adr/0001-fase-1-estructura-contrato-dominio.md)
