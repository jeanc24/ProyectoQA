package icc354.pucmm.proyectoqa.application.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Base para integration tests de seguridad con Keycloak real (TEST-01).
 *
 * <p>Postgres (heredado) + Keycloak Testcontainers importando
 * {@code keycloak/inventory-realm.json}. El perfil {@code docker} activa
 * {@link icc354.pucmm.proyectoqa.config.DockerSecurityConfig}.
 *
 * <p>El client secret del password grant sale de {@code KEYCLOAK_CLIENT_SECRET}
 * (y {@code KEYCLOAK_CLIENT_ID}), vía entorno / {@code .env} inyectado por Gradle.
 * Debe coincidir con el secret del realm importado.
 *
 * <p>Requiere Docker. En CI: {@code ./gradlew integrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"integration", "docker"})
public abstract class AbstractKeycloakIntegrationTest extends AbstractIntegrationTest {

    private static final int KEYCLOAK_PORT = 8080;

    /** Solo para la consola admin del contenedor efímero (no es el usuario demo del realm). */
    private static final String CONTAINER_ADMIN_USER = "kc-it-admin";
    private static final String CONTAINER_ADMIN_PASSWORD = "kc-it-admin";

    @SuppressWarnings("resource")
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
            .withExposedPorts(KEYCLOAK_PORT)
            .withEnv("KEYCLOAK_ADMIN", CONTAINER_ADMIN_USER)
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", CONTAINER_ADMIN_PASSWORD)
            .withEnv("KC_HTTP_PORT", String.valueOf(KEYCLOAK_PORT))
            .withCommand("start-dev", "--import-realm")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(
                            Path.of("keycloak/inventory-realm.json").toAbsolutePath().toString()),
                    "/opt/keycloak/data/import/inventory-realm.json")
            .waitingFor(Wait.forHttp("/realms/inventory")
                    .forPort(KEYCLOAK_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        KEYCLOAK.start();
    }

    @LocalServerPort
    protected int port;

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                AbstractKeycloakIntegrationTest::issuerUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                AbstractKeycloakIntegrationTest::jwkSetUri);
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
        // Admin REST de la app apunta al Keycloak del test (list users, etc. si algún IT lo usa)
        registry.add("app.keycloak.admin-server-url", AbstractKeycloakIntegrationTest::keycloakBaseUrl);
        registry.add("app.keycloak.admin-username", () -> CONTAINER_ADMIN_USER);
        registry.add("app.keycloak.admin-password", () -> CONTAINER_ADMIN_PASSWORD);
        registry.add("app.keycloak.client-id", AbstractKeycloakIntegrationTest::clientId);
    }

    protected static String keycloakBaseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(KEYCLOAK_PORT);
    }

    /**
     * Usa {@link GenericContainer#getHost()} (respeta TESTCONTAINERS_HOST_OVERRIDE)
     * en vez de hardcodear localhost.
     */
    protected static String issuerUri() {
        return keycloakBaseUrl() + "/realms/inventory";
    }

    protected static String jwkSetUri() {
        return issuerUri() + "/protocol/openid-connect/certs";
    }

    protected static String tokenEndpoint() {
        return issuerUri() + "/protocol/openid-connect/token";
    }

    protected String apiUrl(String path) {
        return "http://localhost:" + port + path;
    }

    protected RestClient restClient() {
        return RestClient.create();
    }

    protected static String clientId() {
        return requiredEnv("KEYCLOAK_CLIENT_ID");
    }

    protected static String clientSecret() {
        return requiredEnv("KEYCLOAK_CLIENT_SECRET");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required for Keycloak IT. "
                            + "Copy .env.example → .env (or export the var). "
                            + "Gradle loads .env into the test JVM automatically.");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    protected String getAccessToken(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId());
        form.add("client_secret", clientSecret());
        form.add("username", username);
        form.add("password", password);

        Map<String, Object> body = RestClient.create()
                .post()
                .uri(tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("No access_token for user " + username);
        }
        return body.get("access_token").toString();
    }
}
