# syntax=docker/dockerfile:1.7
# =============================================================================
# Frontend: build con Node y servido con nginx (no root) — multi-stage.
#
#   docker build -t interline-frontend ./frontend
#
# Variables de runtime:
#   PORT          puerto de escucha (Cloud Run lo inyecta; 8080 por defecto)
#   API_UPSTREAM  backend al que nginx reenvía /api/ (mismo origen: sin CORS)
# La configuración white-label (config/runtime-config.json) se puede sustituir montando
# otro archivo, sin reconstruir la imagen.
# =============================================================================
ARG BUILD_IMAGE=mirror.gcr.io/library/node:24.15.0-alpine
ARG RUNTIME_IMAGE=mirror.gcr.io/nginxinc/nginx-unprivileged:1.29-alpine

# ---------------------------------------------------------------- build
FROM ${BUILD_IMAGE} AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    --mount=type=secret,id=extra-ca,required=false \
    if [ -s /run/secrets/extra-ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/extra-ca; fi; \
    npm ci --no-audit --no-fund
COPY . .
RUN npm run build -- --configuration production

# ---------------------------------------------------------------- runtime
FROM ${RUNTIME_IMAGE}
# La imagen oficial procesa /etc/nginx/templates/*.template con envsubst al arrancar.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist/frontend/browser/ /usr/share/nginx/html/
ENV PORT=8080 \
    API_UPSTREAM=http://backend:8080 \
    NGINX_ENVSUBST_FILTER="^(PORT|API_UPSTREAM)$"
EXPOSE 8080
# Usuario nginx (UID 101), sin privilegios: escucha en un puerto > 1024.
USER 101
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/healthz" || exit 1
