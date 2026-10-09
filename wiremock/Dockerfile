# syntax=docker/dockerfile:1.7
# Sandbox del proveedor de vuelos: WireMock con el contrato estilo Amadeus (datos FICTICIOS)
# copiado dentro de la imagen, para desplegarlo en Cloud Run sin volúmenes.
#   docker build -t interline-provider-sandbox ./wiremock
ARG WIREMOCK_IMAGE=mirror.gcr.io/wiremock/wiremock:3.13.2
FROM ${WIREMOCK_IMAGE}
COPY mappings/ /home/wiremock/mappings/
COPY __files/ /home/wiremock/__files/
# Sin privilegios: WireMock solo lee los stubs (no graba peticiones en disco).
USER 65534:65534
EXPOSE 8080
CMD ["--port", "8080", "--disable-banner", "--no-request-journal"]
