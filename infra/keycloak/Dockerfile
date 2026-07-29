# Keycloak afinado para Render free (512MB).
# Build Quarkus aquí; runtime: start --optimized --cache=local.
#   docker build -f infra/keycloak/Dockerfile -t inventory-keycloak .
FROM quay.io/keycloak/keycloak:26.0 AS builder

ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=false
ENV KC_HTTP_ENABLED=true
ENV KC_DB=postgres
# Menos memoria en single-node free tier.
ENV KC_FEATURES_DISABLED=persistent-user-sessions

WORKDIR /opt/keycloak
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.0

COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY keycloak/inventory-realm.json /opt/keycloak/data/import/inventory-realm.json

USER root
COPY infra/keycloak/docker-entrypoint.sh /opt/keycloak/docker-entrypoint-cloud.sh
RUN chmod +x /opt/keycloak/docker-entrypoint-cloud.sh
USER 1000

ENTRYPOINT ["/opt/keycloak/docker-entrypoint-cloud.sh"]
