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
 * <p>Estrategia: Postgres (heredado) + Keycloak Testcontainers importando
 * {@code keycloak/inventory-realm.json}. El perfil {@code docker} activa
 * {@link icc354.pucmm.proyectoqa.config.DockerSecurityConfig} (JWT + @PreAuthorize).
 * Los demás IT de servicio siguen solo con perfil {@code integration} (sin HTTP/JWT).
 *
 * <p>Requiere Docker. En CI: {@code ./gradlew integrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"integration", "docker"})
public abstract class AbstractKeycloakIntegrationTest extends AbstractIntegrationTest {

    private static final int KEYCLOAK_PORT = 8080;

    @SuppressWarnings("resource")
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
            .withExposedPorts(KEYCLOAK_PORT)
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
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
        // Evitar CORS / OTel noise
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
    }

    protected static String issuerUri() {
        return "http://localhost:" + KEYCLOAK.getMappedPort(KEYCLOAK_PORT) + "/realms/inventory";
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

    @SuppressWarnings("unchecked")
    protected String getAccessToken(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "inventory-api");
        form.add("client_secret", "inventory-api-secret");
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
