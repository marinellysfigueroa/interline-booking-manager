# syntax=docker/dockerfile:1.7
# =============================================================================
# Backend en modo JVM — build multi-stage.
#
#   docker build -f backend/Dockerfile -t interline-backend:jvm .     (contexto = raíz del repo)
#
# Las imágenes base son ARG para poder apuntar a un mirror propio (p. ej. un repositorio remoto
# de Artifact Registry). Por defecto se usa mirror.gcr.io (mirror de Docker Hub de Google, sin
# límite de descargas) y gcr.io/distroless.
#
# Secretos de build OPCIONALES (nunca quedan en una capa):
#   --secret id=extra-ca,src=ca.crt              CA de un proxy corporativo con inspección TLS
#   --secret id=maven-settings,src=settings.xml  proxy / mirror (Nexus, Artifactory) de Maven
# =============================================================================
# Maven oficial (misma versión que .mvn/wrapper): no se usa mvnw dentro de la imagen porque, sin
# unzip, el wrapper baja el .tar.gz y su checksum declarado es el del .zip.
ARG BUILD_IMAGE=mirror.gcr.io/library/maven:3.9.16-eclipse-temurin-21-noble
ARG RUNTIME_IMAGE=gcr.io/distroless/java21-debian12:nonroot

# ---------------------------------------------------------------- build
FROM ${BUILD_IMAGE} AS build
# Cada certificado del bundle se importa por separado (keytool solo lee el primero de un archivo).
RUN --mount=type=secret,id=extra-ca,required=false \
    if [ -s /run/secrets/extra-ca ]; then \
      cp /run/secrets/extra-ca /usr/local/share/ca-certificates/extra-ca.crt && update-ca-certificates >/dev/null && \
      awk '/BEGIN CERT/{n++} {print > ("/tmp/extra-ca-" n ".pem")}' /run/secrets/extra-ca && \
      for cert in /tmp/extra-ca-*.pem; do \
        keytool -importcert -noprompt -cacerts -storepass changeit -alias "$(basename "$cert" .pem)" -file "$cert" >/dev/null; \
      done && rm -f /tmp/extra-ca-*.pem; \
    fi
WORKDIR /workspace/backend

# Las dependencias se cachean entre builds con el cache mount de BuildKit en /root/.m2.
COPY backend/pom.xml ./
COPY api/openapi.yaml /workspace/api/openapi.yaml

# Las pruebas corren en CI antes de construir la imagen: aquí solo se empaqueta.
COPY backend/src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven-settings,required=false \
    mvn -B -q $( [ -s /run/secrets/maven-settings ] && echo "-s /run/secrets/maven-settings" ) package -DskipTests

# ---------------------------------------------------------------- runtime
# Distroless: sin shell ni gestor de paquetes; usuario nonroot (UID 65532) por defecto.
FROM ${RUNTIME_IMAGE}
WORKDIR /app
# Layout "fast-jar" de Quarkus: librerías primero (cambian poco) para reaprovechar capas.
COPY --from=build /workspace/backend/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/backend/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/backend/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/backend/target/quarkus-app/quarkus/ /app/quarkus/
USER nonroot
EXPOSE 8080
ENV LANG=C.UTF-8
# MaxRAMPercentage: el heap se ajusta al límite de memoria del contenedor (Cloud Run / compose).
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", \
  "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-jar", "/app/quarkus-run.jar"]
