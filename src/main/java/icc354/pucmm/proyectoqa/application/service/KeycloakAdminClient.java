package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.domain.exception.KeycloakAdminException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Cliente mínimo de la Admin REST API de Keycloak (listado de usuarios + roles de cliente).
 * Usa {@code admin-cli} en el realm master con las credenciales del contenedor Keycloak.
 */
@Component
public class KeycloakAdminClient {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient restClient;
    private final String realm;
    private final String clientId;
    private final String adminRealm;
    private final String adminUsername;
    private final String adminPassword;

    @Autowired
    public KeycloakAdminClient(
            @Value("${app.keycloak.admin-server-url}") String adminServerUrl,
            @Value("${app.keycloak.realm:inventory}") String realm,
            @Value("${app.keycloak.client-id}") String clientId,
            @Value("${app.keycloak.admin-realm:master}") String adminRealm,
            @Value("${app.keycloak.admin-username}") String adminUsername,
            @Value("${app.keycloak.admin-password}") String adminPassword) {
        this(RestClient.builder().baseUrl(trimTrailingSlash(adminServerUrl)).build(),
                realm, clientId, adminRealm, adminUsername, adminPassword);
    }

    /** Visible for unit tests (MockRestServiceServer). */
    KeycloakAdminClient(
            RestClient restClient,
            String realm,
            String clientId,
            String adminRealm,
            String adminUsername,
            String adminPassword) {
        this.restClient = restClient;
        this.realm = realm;
        this.clientId = clientId;
        this.adminRealm = adminRealm;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public List<Map<String, Object>> listUsers(int max) {
        String token = fetchAdminToken();
        try {
            List<Map<String, Object>> users = restClient.get()
                    .uri("/admin/realms/{realm}/users?max={max}", realm, max)
                    .header(AUTHORIZATION, BEARER_PREFIX + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return users != null ? users : List.of();
        } catch (RestClientException ex) {
            throw new KeycloakAdminException("Failed to list users from Keycloak", ex);
        }
    }

    public String resolveClientUuid() {
        String token = fetchAdminToken();
        try {
            List<Map<String, Object>> clients = restClient.get()
                    .uri("/admin/realms/{realm}/clients?clientId={clientId}", realm, clientId)
                    .header(AUTHORIZATION, BEARER_PREFIX + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (clients == null || clients.isEmpty()) {
                throw new KeycloakAdminException("Keycloak client not found: " + clientId);
            }
            Object id = clients.getFirst().get("id");
            if (id == null) {
                throw new KeycloakAdminException("Keycloak client has no id: " + clientId);
            }
            return String.valueOf(id);
        } catch (KeycloakAdminException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new KeycloakAdminException("Failed to resolve Keycloak client id", ex);
        }
    }

    public List<String> listClientRoles(String userId, String clientUuid) {
        String token = fetchAdminToken();
        try {
            List<Map<String, Object>> roles = restClient.get()
                    .uri("/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
                            realm, userId, clientUuid)
                    .header(AUTHORIZATION, BEARER_PREFIX + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (roles == null || roles.isEmpty()) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> String.valueOf(role.get("name")))
                    .sorted()
                    .toList();
        } catch (RestClientException ex) {
            throw new KeycloakAdminException("Failed to list roles for user " + userId, ex);
        }
    }

    private String fetchAdminToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);

        try {
            Map<String, Object> body = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", adminRealm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (body == null || body.get("access_token") == null) {
                throw new KeycloakAdminException("Keycloak admin token response missing access_token");
            }
            return String.valueOf(body.get("access_token"));
        } catch (KeycloakAdminException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new KeycloakAdminException(
                    "Failed to obtain Keycloak admin token (check KEYCLOAK_ADMIN credentials and admin-server-url)",
                    ex);
        }
    }

    static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("app.keycloak.admin-server-url must be set");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
