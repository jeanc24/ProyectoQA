package icc354.pucmm.proyectoqa.application.integration;

import icc354.pucmm.proyectoqa.application.dto.ProductRequest;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.dto.StockMovementRequest;
import icc354.pucmm.proyectoqa.application.dto.StockMovementResponse;
import icc354.pucmm.proyectoqa.application.service.ProductService;
import icc354.pucmm.proyectoqa.application.service.StockService;
import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import icc354.pucmm.proyectoqa.domain.exception.InsufficientStockException;
import icc354.pucmm.proyectoqa.domain.repository.ProductRepository;
import icc354.pucmm.proyectoqa.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración de movimientos de stock y su persistencia en Postgres (Testcontainers).
 */
@Testcontainers
class StockIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ProductResponse createProduct(String sku, int quantity) {
        return productService.create(new ProductRequest(
                "Product " + sku,
                sku,
                "Stock test",
                null,
                new BigDecimal("10.00"),
                quantity,
                2,
                true
        ));
    }

    /**
     * Caso de integración #1: Entrada de stock
     * Verifica que un movimiento IN persiste el registro y actualiza la cantidad del producto.
     */
    @Test
    void registerInMovement_persistsMovementAndUpdatesProduct() {
        ProductResponse product = createProduct("STK-IN-01", 5);

        StockMovementResponse movement = stockService.registerMovement(
                new StockMovementRequest(product.id(), MovementType.IN, 3, "Delivery"),
                "integration-test");

        assertThat(movement.quantityBefore()).isEqualTo(5);
        assertThat(movement.quantityAfter()).isEqualTo(8);
        assertThat(productRepository.findById(product.id()).orElseThrow().getQuantity()).isEqualTo(8);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE product_id = ?",
                Integer.class,
                product.id());
        assertThat(rowCount).isEqualTo(1);
    }

    /**
     * Caso de integración #2: Salida con stock insuficiente
     * Verifica que un movimiento OUT mayor al stock disponible lanza InsufficientStockException.
     */
    @Test
    void registerOutMovement_insufficientStockThrows() {
        ProductResponse product = createProduct("STK-OUT-01", 2);

        assertThatThrownBy(() -> stockService.registerMovement(
                new StockMovementRequest(product.id(), MovementType.OUT, 5, null),
                "integration-test"))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(productRepository.findById(product.id()).orElseThrow().getQuantity()).isEqualTo(2);
    }

    /**
     * Caso de integración #3: Historial por producto
     * Verifica que findByProductId devuelve los movimientos ordenados por fecha descendente.
     */
    @Test
    void findByProductId_returnsMovementHistory() {
        ProductResponse product = createProduct("STK-HIST-01", 10);

        stockService.registerMovement(
                new StockMovementRequest(product.id(), MovementType.OUT, 2, "Sale"),
                "admin");
        stockService.registerMovement(
                new StockMovementRequest(product.id(), MovementType.IN, 5, "Restock"),
                "admin");

        PageResponse<StockMovementResponse> history = stockService.findByProductId(
                product.id(), PageRequest.of(0, 10));

        assertThat(history.content()).hasSize(2);
        assertThat(history.content().get(0).movementType()).isEqualTo(MovementType.IN);
        assertThat(history.content().get(1).movementType()).isEqualTo(MovementType.OUT);
    }
}
