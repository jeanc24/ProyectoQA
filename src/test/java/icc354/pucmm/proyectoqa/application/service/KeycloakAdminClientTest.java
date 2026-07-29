package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.domain.exception.KeycloakAdminException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class KeycloakAdminClientTest {

    private static final String BASE = "http://keycloak:8080";

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(
                builder.build(),
                "inventory",
                "inventory-api",
                "master",
                "admin",
                "admin");
    }

    @Test
    void trimTrailingSlash_removesSlashAndRejectsBlank() {
        assertThat(KeycloakAdminClient.trimTrailingSlash("http://kc/")).isEqualTo("http://kc");
        assertThat(KeycloakAdminClient.trimTrailingSlash("http://kc")).isEqualTo("http://kc");
        assertThatThrownBy(() -> KeycloakAdminClient.trimTrailingSlash("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeycloakAdminClient.trimTrailingSlash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicConstructor_buildsClientWithTrimmedBaseUrl() {
        KeycloakAdminClient built = new KeycloakAdminClient(
                "http://keycloak:8080/",
                "inventory",
                "inventory-api",
                "master",
                "admin",
                "admin");
        assertThat(built).isNotNull();
    }

    @Test
    void listUsers_returnsUsersFromAdminApi() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/inventory/users?max=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withSuccess(
                        "[{\"id\":\"1\",\"username\":\"admin\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.listUsers(10))
                .singleElement()
                .satisfies(u -> assertThat(u.get("username")).isEqualTo("admin"));
        server.verify();
    }

    @Test
    void listUsers_returnsEmptyWhenBodyNull() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/inventory/users?max=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // Empty/non-JSON body can yield null body → empty list
        assertThat(client.listUsers(5)).isEmpty();
    }

    @Test
    void listUsers_wrapsRestErrors() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/inventory/users?max=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.listUsers(1))
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("Failed to list users");
    }

    @Test
    void resolveClientUuid_returnsId() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/inventory/clients?clientId=inventory-api"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withSuccess(
                        "[{\"id\":\"uuid-abc\",\"clientId\":\"inventory-api\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.resolveClientUuid()).isEqualTo("uuid-abc");
        server.verify();
    }

    @Test
    void resolveClientUuid_throwsWhenClientMissing() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/inventory/clients?clientId=inventory-api"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::resolveClientUuid)
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("client not found");
    }

    @Test
    void resolveClientUuid_throwsWhenIdMissing() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/inventory/clients?clientId=inventory-api"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"clientId\":\"inventory-api\"}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::resolveClientUuid)
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("has no id");
    }

    @Test
    void resolveClientUuid_wrapsRestErrors() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/inventory/clients?clientId=inventory-api"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(client::resolveClientUuid)
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("Failed to resolve Keycloak client id");
    }

    @Test
    void listClientRoles_mapsAndSortsNames() {
        expectToken();
        server.expect(requestTo(
                        BASE + "/admin/realms/inventory/users/u1/role-mappings/clients/c1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"name\":\"stock:view\"},{\"name\":\"product:view\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.listClientRoles("u1", "c1"))
                .containsExactly("product:view", "stock:view");
        server.verify();
    }

    @Test
    void listClientRoles_returnsEmptyWhenNoRoles() {
        expectToken();
        server.expect(requestTo(
                        BASE + "/admin/realms/inventory/users/u1/role-mappings/clients/c1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.listClientRoles("u1", "c1")).isEmpty();
    }

    @Test
    void listClientRoles_wrapsRestErrors() {
        expectToken();
        server.expect(requestTo(
                        BASE + "/admin/realms/inventory/users/u1/role-mappings/clients/c1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.listClientRoles("u1", "c1"))
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("Failed to list roles");
    }

    @Test
    void fetchAdminToken_throwsWhenAccessTokenMissing() {
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.listUsers(1))
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("missing access_token");
    }

    @Test
    void fetchAdminToken_wrapsRestErrors() {
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.listUsers(1))
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("Failed to obtain Keycloak admin token");
    }

    @Test
    void keycloakAdminException_constructors() {
        KeycloakAdminException plain = new KeycloakAdminException("plain");
        assertThat(plain.getMessage()).isEqualTo("plain");
        assertThat(plain.getCause()).isNull();

        RuntimeException cause = new RuntimeException("root");
        KeycloakAdminException wrapped = new KeycloakAdminException("wrapped", cause);
        assertThat(wrapped.getMessage()).isEqualTo("wrapped");
        assertThat(wrapped.getCause()).isSameAs(cause);
    }

    private void expectToken() {
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok-1\"}",
                        MediaType.APPLICATION_JSON));
    }
}
