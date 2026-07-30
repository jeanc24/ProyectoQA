package icc354.pucmm.proyectoqa.application.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración de integridad de datos: migraciones Flyway y restricciones de Postgres.
 *
 * Correr Test: .\gradlew.bat integrationTest --tests "*DataIntegrityIntegrationTest"
 */
@Testcontainers
class DataIntegrityIntegrationTest extends AbstractIntegrationTest {

    /**
     * Cliente SQL de Spring. Con él hacemos SELECT/INSERT contra Postgres.
     * Los "?" son parámetros (evitan SQL injection y arman el query con seguridad).
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =========================================================================
    // BLOQUE 1 — Flyway: ¿las migraciones corrieron bien en una BD vacía?
    // =========================================================================

    /**
     * Caso de integración #1: Migraciones Flyway completas
     * Verifica que Flyway aplicó migraciones exitosas y creó las tablas principales del esquema.
     */
    @Test
    @DisplayName("Flyway applied all migrations successfully on empty database")
    void flyway_appliesAllMigrationsOnEmptyDatabase() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class
        );

        // Verificamos que las migraciones se aplicaron correctamente
        assertThat(successfulMigrations).isGreaterThanOrEqualTo(3);

        // Verificamos que las tablas importantes existen de verdad
        assertThat(tableExists("products")).isTrue();
        assertThat(tableExists("categories")).isTrue();
        assertThat(tableExists("stock_movements")).isTrue();
        assertThat(tableExists("products_audit")).isTrue();
        assertThat(tableExists("revinfo")).isTrue(); 
    }

    /**
     * Caso de integración #2: Sin migración seed
     * Verifica que no existe ninguna migración seed que inserte datos de demostración.
     */
    @Test
    @DisplayName("No seed migration present — skip seed assertions")
    void seedData_noSeedMigrationPresent() {

        // Verificamos que no hay ninguna migración seed para que no se inserten datos de demo en la BD
        Integer seedMigrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE success = true
                  AND (script ILIKE '%seed%' OR description ILIKE '%seed%')
                """,
                Integer.class
        );

        assertThat(seedMigrations).isZero();
    }

    // =========================================================================
    // BLOQUE 2 — Constraints: la BD debe RECHAZAR datos inválidos
    // Patrón: intentar algo ilegal → esperamos DataAccessException (error SQL).
    // =========================================================================

    /**
     * Caso de integración #3: Restricción UNIQUE en SKU
     * Verifica que Postgres rechaza un segundo producto con el mismo SKU.
     */
    @Test
    @DisplayName("SKU unique constraint rejects duplicate SKU")
    void constraint_duplicateSku_isRejected() {
        // Se inserta un producto válido con SKU "DATA-SKU-001"
        jdbcTemplate.update(
                """
                INSERT INTO products (name, sku, price, quantity, min_stock, active)
                VALUES (?, ?, ?, ?, ?, TRUE)
                """,
                "Product A", "DATA-SKU-001", 10.00, 1, 0
        );

        // Se intenta insertar otro producto con el mismo SKU, lo cual debe ser rechazado
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO products (name, sku, price, quantity, min_stock, active)
                VALUES (?, ?, ?, ?, ?, TRUE)""",
                "Product B", "DATA-SKU-001", 20.00, 1, 0
        )).isInstanceOf(DataAccessException.class);

        // Se espera que se lance una DataAccessException de postgress para que el test falle
    }

    /**
     * Caso de integración #4: CHECK precio no negativo
     * Verifica que Postgres rechaza un producto con precio negativo.
     */
    @Test
    @DisplayName("CHECK (price >= 0) rejects negative price")
    void constraint_negativePrice_isRejected() {
        //se inserta un producto con un precio negativo, lo cual debe ser rechazado
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO products (name, sku, price, quantity, min_stock, active)
                VALUES (?, ?, ?, ?, ?, TRUE)
                """,
                "Bad Price", "DATA-PRICE-NEG", -1.00, 0, 0
        )).isInstanceOf(DataAccessException.class);
        // Se espera que se lance una DataAccessException de postgress para que el test falle
    }

    /**
     * Caso de integración #5: FK categoría inválida
     * Verifica que Postgres rechaza un producto con category_id que no existe.
     */
    @Test
    @DisplayName("FK category_id rejects unknown category")
    void constraint_invalidCategoryFk_isRejected() {
        // se inserta un producto con un category_id que no existe por ejemplo 999999, lo cual debe ser rechazado
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO products (name, sku, category_id, price, quantity, min_stock, active)
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """,
                "Orphan Product", "DATA-FK-999", 999999L, 5.00, 0, 0
        )).isInstanceOf(DataAccessException.class);
        // Se espera que se lance una DataAccessException de postgress para que el test falle
    }

    /**
     * Caso de integración #6: FK categoría válida
     * Verifica que un producto con category_id existente se inserta correctamente.
     */
    @Test
    @DisplayName("Valid category FK allows product insert")
    void constraint_validCategoryFk_isAccepted() {
        // Caso positivo (el "camino feliz"): si la categoría SÍ existe, el producto se inserta.

        // 1) Creamos una categoría y pedimos el id generado (RETURNING id).
        Long categoryId = jdbcTemplate.queryForObject(
                """
                INSERT INTO categories (name, description)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                "DataTest Category",
                "Created by DataIntegrityIntegrationTest"
        );

        assertThat(categoryId).isNotNull();

        // Se inserta un producto apuntando a esa categoría real
        int rows = jdbcTemplate.update(
                """
                INSERT INTO products (name, sku, category_id, price, quantity, min_stock, active)
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """,
                "Linked Product", "DATA-FK-OK", categoryId, 15.50, 2, 1
        );

        // Se espera que se inserte 1 fila
        assertThat(rows).isEqualTo(1);

        // Se verifica que el producto quedo guardado (lo buscamos por SKU)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE sku = ?",
                Integer.class,
                "DATA-FK-OK"
        );
        assertThat(count).isEqualTo(1);

    }

    // =========================================================================
    // Helpers
    // =========================================================================
    
    // Helper para verificar si una tabla existe en la BD
    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count == 1;
    }
}
