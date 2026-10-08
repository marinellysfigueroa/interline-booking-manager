# ADR 0005 — Fase 5: imágenes Docker y docker-compose

- **Estado:** aceptado
- **Fecha:** 2026-10-08

## Contexto

Necesitamos imágenes de producción para el backend (JVM y nativa) y el frontend, más un
entorno local completo con un solo comando. Las imágenes deben ser mínimas, sin root y sin
secretos, y servir tal cual para Cloud Run en la fase 6.

## Decisiones

### D1. Builds multi-stage desde el código fuente

- Las tres imágenes compilan dentro de Docker (`docker compose up --build` funciona en un
  clon limpio con solo Docker instalado).
- Se sustituyeron los cuatro Dockerfiles del andamiaje de Quarkus, que esperaban
  artefactos precompilados en `target/`.
- **Contexto de build del backend = raíz del repo**, porque el build necesita
  `api/openapi.yaml` (se publica en `META-INF/` y de él se generan las interfaces). Un
  `.dockerignore` en lista blanca deja entrar solo `api/openapi.yaml`,
  `backend/pom.xml` y `backend/src/main/`: ni `.git`, ni `node_modules`, ni `target`,
  ni ningún `.env`.
- Las pruebas **no** corren dentro de la imagen; las ejecuta el pipeline (fase 6) antes
  de construirla.
- Las dependencias se cachean entre builds con `--mount=type=cache` (BuildKit) en
  `/root/.m2` y `/root/.npm`.

### D2. Maven oficial en lugar de `mvnw` dentro de la imagen

Se encontró un fallo del andamiaje: si la imagen no trae `unzip`, el wrapper descarga el
`.tar.gz`, pero `maven-wrapper.properties` declara el SHA-256 del `.zip` y la validación
falla siempre. Por eso:

- la imagen JVM se construye sobre la imagen oficial `maven:3.9.16-eclipse-temurin-21`
  (la misma versión que el wrapper);
- la nativa copia esa instalación de Maven dentro del builder de Mandrel.

En local `./mvnw` sigue funcionando.

### D3. Imágenes base

Todas las bases son `ARG` sobrescribibles, por ejemplo para apuntar a un repositorio remoto
de Artifact Registry.

| Imagen | Build | Runtime |
| --- | --- | --- |
| Backend JVM | `maven:3.9.16-eclipse-temurin-21-noble` | `gcr.io/distroless/java21-debian12:nonroot` |
| Backend nativo | `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25` (el builder por defecto de Quarkus 3.40) | `gcr.io/distroless/base-debian12:nonroot` |
| Frontend | `node:24.15.0-alpine` | `nginxinc/nginx-unprivileged:1.29-alpine` |

- Las imágenes de Docker Hub se piden a **`mirror.gcr.io`**, el mirror oficial de Google,
  para no topar con el límite de descargas de Docker Hub. Pasó en este entorno (HTTP 429)
  y pasa en CI.
- **Distroless** (sin shell ni gestor de paquetes) y **usuarios no root**: `nonroot`
  (UID 65532) en el backend y `nginx` (UID 101) en el frontend, escuchando en el 8080.
- Los archivos de la aplicación pertenecen a root y son de solo lectura para el usuario
  del proceso.
- **Nativo "casi estático":** `-H:+StaticExecutableWithDynamicLibC` enlaza todo salvo
  glibc, así que el ejecutable corre sobre `distroless/base` sin añadir bibliotecas.
  `PersistenceNativeReflectionConfig` registra para reflexión los records del dominio que
  Jackson serializa a JSONB, y los DTO del contrato se registran como se explica en D8.

### D4. JVM en contenedor

- `-XX:MaxRAMPercentage=75`: el heap se ajusta al límite de memoria (768 MiB en compose).
- `-XX:+ExitOnOutOfMemoryError`: que el orquestador reinicie el contenedor en lugar de
  dejarlo degradado.
- `-Dstdout.encoding=UTF-8`: distroless no tiene locale y, sin esta opción, los logs JSON
  mostraban «rechaz?» en lugar de «rechazó». Verificado en el contenedor.
- `quarkus.http.port=${PORT:8080}` para Cloud Run.

### D5. Frontend: nginx como servidor estático y proxy de `/api`

- El plantillado de la imagen oficial (envsubst sobre `templates/`) solo sustituye `PORT`
  y `API_UPSTREAM` (`NGINX_ENVSUBST_FILTER`).
- **Mismo origen:** nginx reenvía `/api/` al backend. Así la SPA no necesita CORS y
  `apiBaseUrl` queda vacío. Con un upstream de Cloud Run (`https://…run.app`) se envía
  el `Host` del upstream y SNI (`proxy_ssl_server_name on`).
- **SPA:** `try_files $uri $uri/ /index.html`.
- **gzip** para JS, CSS, JSON, Problem+JSON y SVG.
- **Caché:**
  - un año e `immutable` solo para los archivos con hash en el nombre
    (`main-AB12CD34.js`);
  - `no-cache` para `index.html`, `/config/` (la configuración white-label) y los logos.
- **Cabeceras de seguridad** en cada `location` (en nginx, un `add_header` dentro de una
  location anula los heredados): CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy` y
  `Permissions-Policy`.
  - La CSP permite `script-src 'self'` gracias a que se desactivó `inlineCritical` en
    `angular.json`: el critical CSS inyectaba un `<script>` inline.
  - `style-src` necesita `'unsafe-inline'` porque Angular inserta los estilos de los
    componentes como `<style>`.
- `HEALTHCHECK` con `wget` a `/healthz`. La configuración white-label se cambia montando
  otro `runtime-config.json`, sin reconstruir.

### D6. Secretos de build opcionales (redes corporativas)

`--secret id=extra-ca` (CA de un proxy con inspección TLS) y `--secret id=maven-settings`
(proxy o mirror Nexus/Artifactory) se montan solo durante el `RUN` y **nunca quedan en
una capa**. Sin ellos la build funciona igual. Así se construyeron las imágenes en este
entorno, que sale por un proxy con CA propia.

### D7. docker-compose

- **Servicios:**
  - `postgres:17-alpine` con volumen y healthcheck `pg_isready`;
  - WireMock 3.13.2 con `./wiremock` montado en solo lectura (el mismo contrato que los
    Dev Services);
  - backend (perfil prod, configurado solo por variables de entorno);
  - frontend en `:4200`.
- Jaeger es opcional, con `--profile observability`.
- `depends_on` con `condition: service_healthy` para la base de datos y WireMock. El
  backend distroless no tiene shell para un healthcheck, así que nginx simplemente
  devuelve 502 en `/api` durante los segundos que tarda en arrancar.
- **Sin secretos en el repo:** `docker-compose.yml` exige las variables con
  `${VAR:?mensaje}`. `.env.example` documenta los valores de desarrollo y `.env` está en
  el `.gitignore` raíz, que no existía y se creó en esta fase (también cubre los
  `*.tfstate` de la fase 6).
- `BACKEND_DOCKERFILE=backend/Dockerfile.native` cambia a la imagen nativa.

## Verificación

- Imágenes construidas en este entorno: backend JVM de 388 MB y frontend de 82 MB.
- `docker compose up`:
  - readiness `UP` con la base de datos en unos 6 s;
  - `/api` vía nginx con `X-Correlation-ID` propagado y Problem Details intactos;
  - fallback de la SPA, gzip, caché y cabeceras CSP comprobados con `curl`;
  - procesos con UID no root;
  - logs JSON en UTF-8.
- **E2E de Playwright contra el stack de compose** (`E2E_BASE_URL=http://localhost:4200
  npm run e2e`): 3/3. Esto valida también que la CSP no rompe la aplicación.
- **Nativo**: ver D8.

### D8. Ejecutable nativo: verificación y un fallo que solo aparecía en nativo

El builder de Mandrel (quay.io) no es accesible desde el entorno en el que se desarrolló el
proyecto. Por eso el ejecutable se compiló con **Oracle GraalVM 25.0.4**, la misma base
JDK 25 que el Mandrel `jdk-25`, descargado con verificación SHA-256. Después se empaquetó
con la etapa runtime exacta de `Dockerfile.native`.

| | JVM (distroless java21) | Nativo (distroless base) |
| --- | --- | --- |
| Imagen | 388 MB | 217 MB |
| Arranque | ≈ 6 s | **0,196 s** (Quarkus) / 0,5 s hasta readiness |
| Memoria en reposo / tras la e2e | — | 15 MiB / 39 MiB (límite 256 MiB) |
| Compilación | ≈ 2,5 min | ≈ 10,5 min, pico de 5,7 GB de RAM |

- `ldd` confirma el enlace casi estático: el ejecutable solo depende de `libc`.
- La e2e de Playwright pasa 3/3 contra el backend nativo, sin ningún WARN ni ERROR en
  sus logs.
- **Hallazgo:** en nativo los Problem Details salían **sin `code` ni `detail`**. Los
  recursos devuelven `Response`, así que Quarkus no sabe qué DTO se serializa y no los
  registra para reflexión. Jackson solo veía las propiedades del constructor
  `@JsonCreator` y perdía las asignadas con métodos fluidos (`code`, `detail`, `fare`,
  `contact`…). La e2e no lo detectaba porque no comprueba esos campos.
  - **Corrección:** openapi-generator anota cada DTO con `@RegisterForReflection`
    (`additionalModelTypeAnnotations`). Los enums generados, que el generador no anota,
    se registran en `RestNativeReflectionConfig`, dentro del adaptador REST, para respetar
    la regla de ArchUnit de que los DTO no salen de ese paquete.
  - **Prevención:** `NativeReflectionCoverageTest` (JVM, milisegundos) falla si algún DTO
    del contrato queda sin registrar.
- `-H:+StaticExecutableWithDynamicLibC` es experimental en GraalVM 25, así que se
  envuelve con `-H:+UnlockExperimentalVMOptions` / `-H:-UnlockExperimentalVMOptions`.

## Consecuencias

- **+** Imágenes pequeñas, sin shell ni root y reproducibles desde el código.
- **+** La misma imagen sirve para compose y para Cloud Run; solo cambian las variables
  de entorno.
- **−** La build nativa necesita unos 6 GB de RAM y varios minutos. En CI se construirá
  solo en la rama principal o bajo demanda (fase 6).
- **−** `mirror.gcr.io` solo cachea imágenes populares de Docker Hub. Si una etiqueta no
  está disponible, se puede usar `--build-arg BUILD_IMAGE=docker.io/...`.
