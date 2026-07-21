package icc354.pucmm.proyectoqa.application.integration;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST-02 — Contract testing: respuestas HTTP validadas contra la spec OpenAPI (/api-docs).
 *
 * <p>Perfil {@code local} deja la API abierta (sin JWT). Postgres vía Testcontainers.
 * Usa Atlassian swagger-request-validator contra el documento generado por springdoc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"integration", "local"})
@Tag("contract")
@Testcontainers
class OpenApiContractIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient client;
    private OpenApiInteractionValidator validator;

    @BeforeEach
    void setUp() {
        client = RestClient.create();
        String specUrl = "http://localhost:" + port + "/api-docs";
        validator = OpenApiInteractionValidator.createForSpecificationUrl(specUrl).build();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void assertValidResponse(String path, Request.Method method, int status, String body) {
        SimpleResponse response = SimpleResponse.Builder.status(status)
                .withContentType(MediaType.APPLICATION_JSON_VALUE)
                .withBody(body == null ? "" : body)
                .build();

        ValidationReport report = validator.validateResponse(path, method, response);
        assertThat(report.getMessages())
                .as("OpenAPI contract violations for %s %s → %s%n%s",
                        method, path, status, report)
                .noneMatch(m -> m.getLevel() == ValidationReport.Level.ERROR);
    }

    @Test
    void apiDocs_isAvailableAndListsRequiredPaths() {
        ResponseEntity<Map> spec = client.get()
                .uri(url("/api-docs"))
                .retrieve()
                .toEntity(Map.class);

        assertThat(spec.getStatusCode().value()).isEqualTo(200);
        assertThat(spec.getBody()).isNotNull();
        assertThat(spec.getBody()).containsKey("paths");

        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) spec.getBody().get("paths");
        assertThat(paths.keySet()).anyMatch(p -> p.startsWith("/api/v1/products"));
        assertThat(paths.keySet()).anyMatch(p -> p.contains("/stock/movements") || p.contains("stock"));
        assertThat(paths.keySet()).anyMatch(p -> p.startsWith("/api/v1/reports"));
        assertThat(paths.keySet()).anyMatch(p -> p.startsWith("/api/v1/audit"));
    }

    @Test
    void productsList_matchesOpenApiContract() {
        ResponseEntity<String> response = client.get()
                .uri(url("/api/v1/products?size=5"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertValidResponse("/api/v1/products", Request.Method.GET, 200, response.getBody());
    }

    @Test
    void productCreateGetAndAudit_matchOpenApiContract() {
        String createBody = """
                {
                  "name": "Contract Laptop",
                  "sku": "CONTRACT-SKU-001",
                  "description": "OpenAPI contract product",
                  "categoryId": null,
                  "price": 199.99,
                  "quantity": 4,
                  "minStock": 1,
                  "active": true
                }
                """;

        ResponseEntity<String> created = client.post()
                .uri(url("/api/v1/products"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .toEntity(String.class);

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertValidResponse("/api/v1/products", Request.Method.POST, 201, created.getBody());

        // id from JSON
        String id = created.getBody().replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1");
        assertThat(id).matches("\\d+");

        ResponseEntity<String> byId = client.get()
                .uri(url("/api/v1/products/" + id))
                .retrieve()
                .toEntity(String.class);
        assertThat(byId.getStatusCode().value()).isEqualTo(200);
        assertValidResponse("/api/v1/products/{id}", Request.Method.GET, 200, byId.getBody());

        ResponseEntity<String> audit = client.get()
                .uri(url("/api/v1/audit/products/" + id))
                .retrieve()
                .toEntity(String.class);
        assertThat(audit.getStatusCode().value()).isEqualTo(200);
        assertValidResponse("/api/v1/audit/products/{id}", Request.Method.GET, 200, audit.getBody());
    }

    @Test
    void stockMovements_matchOpenApiContract() {
        ResponseEntity<String> response = client.get()
                .uri(url("/api/v1/stock/movements?size=5"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertValidResponse("/api/v1/stock/movements", Request.Method.GET, 200, response.getBody());
    }

    @Test
    void reportsInventorySummary_matchOpenApiContract() {
        ResponseEntity<String> response = client.get()
                .uri(url("/api/v1/reports/inventory-summary"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertValidResponse("/api/v1/reports/inventory-summary", Request.Method.GET, 200, response.getBody());
    }

    @Test
    void reportsLowStockAndTopProducts_matchOpenApiContract() {
        ResponseEntity<String> low = client.get()
                .uri(url("/api/v1/reports/low-stock?limit=10"))
                .retrieve()
                .toEntity(String.class);
        assertThat(low.getStatusCode().value()).isEqualTo(200);
        assertValidResponse("/api/v1/reports/low-stock", Request.Method.GET, 200, low.getBody());

        ResponseEntity<String> top = client.get()
                .uri(url("/api/v1/reports/top-products?limit=5"))
                .retrieve()
                .toEntity(String.class);
        assertThat(top.getStatusCode().value()).isEqualTo(200);
        assertValidResponse("/api/v1/reports/top-products", Request.Method.GET, 200, top.getBody());
    }

    @Test
    void categories_matchOpenApiContract() {
        ResponseEntity<String> response = client.get()
                .uri(url("/api/v1/categories"))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertValidResponse("/api/v1/categories", Request.Method.GET, 200, response.getBody());
    }
}
