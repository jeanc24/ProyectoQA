package icc354.pucmm.proyectoqa.application.contract;

import icc354.pucmm.proyectoqa.controller.AuditController;
import icc354.pucmm.proyectoqa.controller.CategoryController;
import icc354.pucmm.proyectoqa.controller.ProductController;
import icc354.pucmm.proyectoqa.controller.ReportController;
import icc354.pucmm.proyectoqa.controller.StockController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract stage (CICD-01): superficie HTTP documentada vs controladores
 * y colección Newman de post-deploy.
 */
@Tag("contract")
class OpenApiContractTest {

    @Test
    void controllersExposeCoreApiPaths() {
        assertPath(ProductController.class, "/api/v1/products");
        assertPath(StockController.class, "/api/v1/stock/movements");
        assertPath(ReportController.class, "/api/v1/reports");
        assertPath(CategoryController.class, "/api/v1/categories");
        assertPath(AuditController.class, "/api/v1/audit/products");
    }

    @Test
    void newmanCollectionCoversCoreApiPaths() throws Exception {
        Path collection = Path.of("docs/final/ci/post-deploy-smoke.collection.json");
        assertTrue(Files.isRegularFile(collection), "Newman collection missing: " + collection);
        String json = Files.readString(collection);
        assertTrue(json.contains("/actuator/health"), "collection must include health");
        assertTrue(json.contains("/api/v1/products"), "collection must include products");
    }

    private static void assertPath(Class<?> controller, String expected) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertNotNull(mapping, controller.getSimpleName() + " missing @RequestMapping");
        boolean match = Arrays.stream(mapping.value()).anyMatch(expected::equals)
                || Arrays.stream(mapping.path()).anyMatch(expected::equals);
        assertTrue(match, controller.getSimpleName() + " expected mapping " + expected);
    }
}
