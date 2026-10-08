# ADR 0006 — Fase 6: GCP con Terraform y CI/CD con GitHub Actions

- **Estado:** propuesto (pendiente de revisión)
- **Fecha:** 2026-10-08

## Contexto

Desplegar el sistema en GCP (Cloud Run, Cloud SQL, Secret Manager, Artifact Registry) con
infraestructura como código y un pipeline que construya, pruebe, analice, publique y
despliegue. Las restricciones son explícitas: **sin llaves JSON** (Workload Identity
Federation) y **ningún secreto en el repositorio ni en el state de Terraform**.

## Decisiones

### D1. Dos stacks de Terraform

- **`bootstrap`:** lo aplica una persona una vez. Contiene lo que necesita existir antes de
  que CI pueda autenticarse: APIs, bucket del state versionado y sin acceso público, pool y
  proveedor WIF, las tres cuentas de CI con roles acotados, y los contenedores de los
  secretos externos.
- **`app`:** lo aplica CI. Backend GCS con configuración parcial (`-backend-config`), de modo
  que el nombre del bucket no queda escrito en el código.
- Proveedores fijados: `google ~> 8.6` y `random ~> 3.9`. Los lock files incluyen los hashes
  `zh:` de todas las plataformas, tomados de los `SHA256SUMS` oficiales de HashiCorp.

### D2. Secretos fuera del state

- **Contraseña de Cloud SQL:** se genera con `ephemeral "random_password"` y se escribe con
  argumentos write-only: `secret_data_wo` en Secret Manager y `password_wo` en Cloud SQL. Así
  se cumple la restricción tal cual. Las alternativas descartadas:
  - `random_password` normal: deja la contraseña en el state.
  - Autenticación IAM de Cloud SQL: no necesita contraseña, pero los usuarios IAM no pueden
    crear tablas en el esquema `public` de PostgreSQL 15+ sin un `GRANT` previo, que solo
    puede dar un usuario con contraseña. Además añade el socket factory de Cloud SQL al
    backend, también en el ejecutable nativo.
- La rotación consiste en subir `db_password_version`: el secreto y el usuario cambian en el
  mismo apply.
- **Credenciales del proveedor:** Terraform crea solo el contenedor del secreto y el valor se
  carga con `gcloud ... --data-file=-`.
- Cloud Run inyecta los secretos con `secret_key_ref`. La cuenta del backend solo tiene
  `secretAccessor` sobre sus propios secretos.

### D3. Red: Cloud SQL privado y backend no expuesto

- Cloud SQL solo tiene **IP privada** (Private Services Access) y `ssl_mode =
  ENCRYPTED_ONLY`. El JDBC usa `sslmode=require`.
- Cloud Run usa **Direct VPC egress**, sin conector serverless y sin coste fijo:
  - el **backend** tiene ingress `INTERNAL_ONLY` y egress `PRIVATE_RANGES_ONLY`, es decir,
    la base de datos por la VPC e internet directo para el proveedor;
  - el **frontend** tiene ingress público y egress `ALL_TRAFFIC`. Con Private Google Access
    en la subred, sus llamadas a `*.run.app` cuentan como internas y llegan al backend.
- Desde internet solo se alcanza el frontend; nginx reenvía `/api` al backend.
- `invoker_iam_disabled = true` en el backend: el ingress interno ya restringe el origen y
  así nginx no tiene que firmar ID tokens.

### D4. Imágenes y despliegues: Terraform crea, CI despliega

- Terraform crea los servicios con una imagen inicial e ignora el campo `image`
  (`lifecycle.ignore_changes`). CI despliega con `gcloud run services update --image`
  usando el SHA del commit como tag.
- Las sondas (`startup` en `/q/health/started`, `liveness` en `/q/health/live` y
  `/healthz`) responden también con la imagen inicial, así que el primer apply no falla.
- Artifact Registry tiene **tags inmutables** y políticas de limpieza: conserva las 15
  imágenes más recientes y borra el resto pasados 30 días.
- Logs: `QUARKUS_LOG_CONSOLE_JSON_LOG_FORMAT=gcp`. Quarkus 3.40 emite directamente el formato
  estructurado de Cloud Logging (`severity`…), sin dependencias extra.
- **Sandbox del proveedor:** `wiremock/Dockerfile` (WireMock sin root con el contrato dentro)
  se despliega como servicio Cloud Run público con datos ficticios. Con un proveedor real:
  `deploy_provider_sandbox = false` y `amadeus_base_url`.

### D5. Workload Identity Federation con permisos por rama

- El proveedor WIF tiene la condición `assertion.repository == '<owner/repo>'`.
- Se mapea un atributo compuesto, `repository_ref = repository + '@' + ref`, que permite
  dar permisos a una rama concreta:

| Cuenta | Puede usarla | Para qué | Roles |
| --- | --- | --- | --- |
| `gh-deployer` | solo `main` | push de imágenes y `run services update` | `run.developer`, `artifactregistry.writer` (sobre el repo), `actAs` sobre las 3 cuentas de runtime |
| `gh-terraform` | solo `main`, con aprobación del environment `infrastructure` | `terraform apply` del stack app | roles de administración de los servicios usados (sin Owner ni Editor) |
| `gh-terraform-plan` | cualquier rama del repo (PR) | `terraform plan` | `viewer`, `iam.securityReviewer`, `secretmanager.viewer` y escritura en el bucket del state (lock) |

Las pruebas detectaron un error real en el bootstrap: un `for_each` sobre los emails de las
cuentas, que solo se conocen tras el apply y habrían roto el primer `terraform apply`. Se
cambió por claves estáticas.

### D6. Pipeline

**`ci.yml`** (PR y push):

1. Lint del contrato (Redocly).
2. Backend: `mvn verify` con Dev Services sobre el Docker del runner.
3. Frontend:
   - los tipos generados deben coincidir con el contrato (`git diff --exit-code`);
   - Prettier, Vitest y build estricto.
4. Análisis estático:
   - actionlint con shellcheck;
   - hadolint;
   - `terraform fmt`, `validate` y **`test`** (mocks);
   - tflint;
   - Trivy en modo `config` (Terraform, Dockerfiles, compose).
5. **CodeQL** para Java y TypeScript (`build-mode: none`, consultas `security-and-quality`).
6. Imágenes con Buildx (caché GHA):
   - Trivy de imágenes, que falla con CRITICAL con fix disponible;
   - `docker compose up --no-build`;
   - **e2e de Playwright contra el stack con las imágenes que se van a publicar**.
7. Solo en `main`:
   - WIF y push a Artifact Registry;
   - despliegue en el orden sandbox → backend → frontend, en el environment `production`;
   - smoke test (`/healthz`, `/api/v1/airports`, configuración runtime).

**`workflow_dispatch`** con `backend_variant: native` construye y despliega el backend
nativo.

**`infra.yml`:**

- en un PR: `plan` con la cuenta de solo lectura, con el resultado en el resumen del job.
  Los PR desde forks no reciben token OIDC y se omiten.
- en `main`: `plan` + `apply` tras aprobación del environment `infrastructure`.
- `concurrency` evita dos planes o applies a la vez sobre el mismo state.

`dependabot.yml` actualiza cada semana las acciones, Maven, npm, las imágenes base y los
providers.

## Verificación (sin credenciales de GCP)

- **Terraform:** `validate` y `fmt` limpios, tflint limpio y **`terraform test` 8/8** con
  mock providers. Las pruebas cubren:
  - la contraseña fuera del state;
  - Cloud SQL privado con TLS y PITR;
  - el backend con ingress interno;
  - el frontend con egress por la VPC y PGA;
  - ningún secreto en claro en las variables de entorno;
  - el sandbox opcional;
  - WIF limitado al repositorio y a `main`;
  - el bucket versionado;
  - ningún rol Owner ni Editor.
- **Workflows:** actionlint + shellcheck limpios.
- **Dockerfiles:** hadolint limpio. DL3006 se ignora porque las etiquetas están fijadas en los
  `ARG`, y DL3002 solo en la etapa de build del nativo.
- **Trivy config:** limpio. Hay un falso positivo documentado (AVD-GCP-0015 comprueba el
  atributo retirado `require_ssl`).
- **Trivy de imágenes:** sin CRITICAL.
  - El frontend pasó de 43 a 1 HIGH con fix tras subir a `nginx-unprivileged:1.30-alpine`
    (Alpine 3.24).
  - Los HIGH restantes del backend (`libexpat`, `lcms2`) vienen de la base distroless y se
    corrigen cuando Google la republica.
- El job `images` se reprodujo en local: build, compose y e2e 3/3.
- **No verificado aquí:** `apply` real en GCP, ejecución en GitHub Actions y la etapa de
  build con Mandrel (quay.io bloqueado en este entorno).

## Consecuencias

- **+** Ninguna credencial de larga duración: ni llaves de cuenta de servicio ni GitHub
  secrets.
- **+** Las garantías de seguridad del Terraform son pruebas ejecutables en cada PR.
- **−** El primer despliegue requiere aplicar `infra.yml` antes de que `ci.yml` pueda
  desplegar (está documentado).
- **−** Las acciones de GitHub se fijan por versión mayor, no por SHA. Dependabot las mantiene;
  para endurecer más, conviene fijarlas por SHA.
- **Pendiente:**
  - exportar trazas a Cloud Trace (hoy se desactiva OTel en GCP);
  - Managed Prometheus para `/q/metrics`;
  - dominio propio con balanceador y Cloud Armor;
  - activar OIDC con un IdP (Identity Platform).
