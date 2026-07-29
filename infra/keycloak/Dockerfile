# Keycloak optimizado para PaaS con poca RAM (Render free/starter ~512MB).
# El `kc.sh build` corre en build-time; runtime usa `start --optimized`.
#   docker build -f infra/keycloak/Dockerfile -t inventory-keycloak .
FROM quay.io/keycloak/keycloak:26.0 AS builder

ENV KC_HEALTH_ENABLED=true
ENV KC_HTTP_ENABLED=true
ENV KC_DB=postgres

WORKDIR /opt/keycloak
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.0

COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY keycloak/inventory-realm.json /opt/keycloak/data/import/inventory-realm.json

# Render inyecta PORT; el entrypoint mapea DATABASE_URL → KC_DB_*.
USER root
COPY infra/keycloak/docker-entrypoint.sh /opt/keycloak/docker-entrypoint-cloud.sh
RUN chmod +x /opt/keycloak/docker-entrypoint-cloud.sh
USER 1000

ENTRYPOINT ["/opt/keycloak/docker-entrypoint-cloud.sh"]
