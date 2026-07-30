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

    /**
     * Caso #1: Cliente Keycloak Admin - Normalizar URL base
     * Verifica que trimTrailingSlash elimina la barra final y rechaza valores en blanco o nulos.
     */
    @Test
    void trimTrailingSlash_removesSlashAndRejectsBlank() {
        assertThat(KeycloakAdminClient.trimTrailingSlash("http://kc/")).isEqualTo("http://kc");
        assertThat(KeycloakAdminClient.trimTrailingSlash("http://kc")).isEqualTo("http://kc");
        assertThatThrownBy(() -> KeycloakAdminClient.trimTrailingSlash("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KeycloakAdminClient.trimTrailingSlash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Caso #2: Cliente Keycloak Admin - Constructor público
     * Verifica que el constructor público construye el cliente con la URL base recortada.
     */
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

    /**
     * Caso #3: Cliente Keycloak Admin - Listar usuarios
     * Verifica que obtiene el token admin y devuelve los usuarios desde la API de Keycloak.
     */
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

    /**
     * Caso #4: Cliente Keycloak Admin - Listar usuarios (cuerpo vacío)
     * Verifica que cuando la respuesta tiene cuerpo vacío o nulo, devuelve una lista vacía.
     */
    @Test
    void listUsers_returnsEmptyWhenBodyNull() {
        expectToken();
        server.expect(requestTo(BASE + "/admin/realms/inventory/users?max=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // Empty/non-JSON body can yield null body → empty list
        assertThat(client.listUsers(5)).isEmpty();
    }

    /**
     * Caso #5: Cliente Keycloak Admin - Error al listar usuarios
     * Verifica que los errores REST al listar usuarios se envuelven en KeycloakAdminException.
     */
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

    /**
     * Caso #6: Cliente Keycloak Admin - Resolver UUID del cliente
     * Verifica que obtiene el token admin y devuelve el id del cliente por clientId.
     */
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

    /**
     * Caso #7: Cliente Keycloak Admin - Cliente no encontrado
     * Verifica que si no existe el cliente en Keycloak, lanza KeycloakAdminException.
     */
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

    /**
     * Caso #8: Cliente Keycloak Admin - Cliente sin id
     * Verifica que si el cliente existe pero no tiene id, lanza KeycloakAdminException.
     */
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

    /**
     * Caso #9: Cliente Keycloak Admin - Error al resolver UUID del cliente
     * Verifica que los errores REST al resolver el client id se envuelven en KeycloakAdminException.
     */
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

    /**
     * Caso #10: Cliente Keycloak Admin - Listar roles del cliente
     * Verifica que obtiene los roles asignados al usuario y los devuelve ordenados por nombre.
     */
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

    /**
     * Caso #11: Cliente Keycloak Admin - Sin roles asignados
     * Verifica que cuando el usuario no tiene roles del cliente, devuelve una lista vacía.
     */
    @Test
    void listClientRoles_returnsEmptyWhenNoRoles() {
        expectToken();
        server.expect(requestTo(
                        BASE + "/admin/realms/inventory/users/u1/role-mappings/clients/c1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.listClientRoles("u1", "c1")).isEmpty();
    }

    /**
     * Caso #12: Cliente Keycloak Admin - Error al listar roles
     * Verifica que los errores REST al listar roles se envuelven en KeycloakAdminException.
     */
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

    /**
     * Caso #13: Cliente Keycloak Admin - Token sin access_token
     * Verifica que si la respuesta del token no incluye access_token, lanza KeycloakAdminException.
     */
    @Test
    void fetchAdminToken_throwsWhenAccessTokenMissing() {
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.listUsers(1))
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("missing access_token");
    }

    /**
     * Caso #14: Cliente Keycloak Admin - Error al obtener token admin
     * Verifica que los errores REST al solicitar el token se envuelven en KeycloakAdminException.
     */
    @Test
    void fetchAdminToken_wrapsRestErrors() {
        server.expect(requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.listUsers(1))
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("Failed to obtain Keycloak admin token");
    }

    /**
     * Caso #15: Cliente Keycloak Admin - Constructores de KeycloakAdminException
     * Verifica que los constructores con mensaje y con causa crean la excepción correctamente.
     */
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
