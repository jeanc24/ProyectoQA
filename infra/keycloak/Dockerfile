#
# Imagen Keycloak (IdP) — importa el realm demo al arrancar.
#
# Build:  docker build -f infra/keycloak/Dockerfile -t inventory-keycloak .
# Compose / Render usan esta imagen; el realm vive en:
#   keycloak/inventory-realm.json  (ver keycloak/README.md para el mapa de bloques)
#
# Flujo:
#   1) Stage builder → kc.sh build (Quarkus optimizado, menos RAM)
#   2) Stage final  → copia binarios + realm JSON + entrypoint
#   3) Entrypoint   → start-dev|start --import-realm
#

# --- Stage 1: build optimizado ---
FROM quay.io/keycloak/keycloak:26.0 AS builder

ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=false
ENV KC_HTTP_ENABLED=true
ENV KC_DB=postgres
# Menos memoria en single-node free tier (Render).
ENV KC_FEATURES_DISABLED=persistent-user-sessions

WORKDIR /opt/keycloak
RUN /opt/keycloak/bin/kc.sh build

# --- Stage 2: runtime ---
FROM quay.io/keycloak/keycloak:26.0

COPY --from=builder /opt/keycloak/ /opt/keycloak/

# Realm inventory: clients, 7 permisos, usuarios demo (admin, viewer, …)
COPY keycloak/inventory-realm.json /opt/keycloak/data/import/inventory-realm.json

USER root
# Script que traduce env de Render/Compose y lanza kc.sh --import-realm
COPY infra/keycloak/docker-entrypoint.sh /opt/keycloak/docker-entrypoint-cloud.sh
RUN chmod +x /opt/keycloak/docker-entrypoint-cloud.sh
USER 1000

ENTRYPOINT ["/opt/keycloak/docker-entrypoint-cloud.sh"]
