package icc354.pucmm.proyectoqa.application.integration;

import icc354.pucmm.proyectoqa.application.dto.ProductRequest;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.service.ProductService;
import icc354.pucmm.proyectoqa.domain.exception.DuplicateSkuException;
import icc354.pucmm.proyectoqa.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Commit;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pruebas de integración del ciclo de vida de productos contra Postgres real (Testcontainers).
 */
@Testcontainers
class ProductIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private ProductRequest sampleRequest(String sku) {
        return new ProductRequest(
                "Laptop Pro",
                sku,
                "Integration test product",
                null,
                new BigDecimal("999.99"),
                5,
                2,
                true
        );
    }

    /**
     * Caso de integración #1: Migraciones Flyway
     * Verifica que Flyway aplicó al menos tres migraciones exitosas en la base de datos.
     */
    @Test
    void flyway_appliesMigrations() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class
        );

        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    /**
     * Caso de integración #2: Crear y consultar producto
     * Verifica que un producto creado se persiste y puede recuperarse por ID desde la BD.
     */
    @Test
    void createAndFindProduct_persistsToDatabase() {
        ProductResponse created = productService.create(sampleRequest("INT-001"));

        assertThat(created.id()).isNotNull();
        assertThat(created.sku()).isEqualTo("INT-001");

        ProductResponse found = productService.findById(created.id());
        assertThat(found.name()).isEqualTo("Laptop Pro");
        assertThat(productRepository.existsBySku("INT-001")).isTrue();
    }

    /**
     * Caso de integración #3: Actualizar producto
     * Verifica que una actualización modifica nombre, precio y cantidad en la base de datos.
     */
    @Test
    void updateProduct_changesFields() {
        ProductResponse created = productService.create(sampleRequest("INT-002"));

        ProductRequest update = new ProductRequest(
                "Laptop Updated",
                "INT-002",
                "Updated description",
                null,
                new BigDecimal("1099.99"),
                10,
                3,
                true
        );

        ProductResponse updated = productService.update(created.id(), update);

        assertThat(updated.name()).isEqualTo("Laptop Updated");
        assertThat(updated.price()).isEqualByComparingTo("1099.99");
        assertThat(updated.quantity()).isEqualTo(10);
    }

    /**
     * Caso de integración #4: Eliminar producto
     * Verifica que al eliminar un producto ya no existe en el repositorio.
     */
    @Test
    void deleteProduct_removesRow() {
        ProductResponse created = productService.create(sampleRequest("INT-003"));

        productService.delete(created.id());

        assertThat(productRepository.existsById(created.id())).isFalse();
    }

    /**
     * Caso de integración #5: SKU duplicado
     * Verifica que crear un producto con SKU ya existente lanza DuplicateSkuException.
     */
    @Test
    void createProduct_duplicateSkuThrows() {
        productService.create(sampleRequest("INT-DUP"));

        assertThatThrownBy(() -> productService.create(sampleRequest("INT-DUP")))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("INT-DUP");
    }

    /**
     * Caso de integración #6: Auditoría en actualización
     * Verifica que al actualizar un producto se registra al menos una fila en products_audit.
     */
    @Test
    void updateProduct_writesAuditRow() {
        ProductResponse created = productService.create(sampleRequest("INT-AUDIT"));

        ProductRequest update = new ProductRequest(
                "Laptop Audited",
                "INT-AUDIT",
                "Audit trail test",
                null,
                new BigDecimal("899.99"),
                7,
                2,
                true
        );

        productService.update(created.id(), update);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products_audit WHERE id = ?",
                Integer.class,
                created.id()
        );

        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }
}
