# Keycloak con realm inventory embebido (sin bind mount).
# Necesario cuando docker compose corre desde Jenkins-en-Docker:
# el daemon del host no puede montar paths de /var/jenkins_home/...
FROM quay.io/keycloak/keycloak:26.0
COPY keycloak/inventory-realm.json /opt/keycloak/data/import/inventory-realm.json
