# Keycloak con realm inventory embebido (build context = raíz del repo).
#   docker build -f infra/keycloak/Dockerfile -t inventory-keycloak .
FROM quay.io/keycloak/keycloak:26.0

COPY keycloak/inventory-realm.json /opt/keycloak/data/import/inventory-realm.json

# Render inyecta PORT; Keycloak usa --http-port / KC_HTTP_PORT.
USER root
COPY infra/keycloak/docker-entrypoint.sh /opt/keycloak/docker-entrypoint-cloud.sh
RUN chmod +x /opt/keycloak/docker-entrypoint-cloud.sh
USER 1000

ENTRYPOINT ["/opt/keycloak/docker-entrypoint-cloud.sh"]
