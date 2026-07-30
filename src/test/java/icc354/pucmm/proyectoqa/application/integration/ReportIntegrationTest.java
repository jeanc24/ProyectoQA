package icc354.pucmm.proyectoqa.application.integration;

import icc354.pucmm.proyectoqa.application.dto.InventorySummaryResponse;
import icc354.pucmm.proyectoqa.application.dto.ProductRequest;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.dto.StockMovementRequest;
import icc354.pucmm.proyectoqa.application.dto.TopProductResponse;
import icc354.pucmm.proyectoqa.application.service.ProductService;
import icc354.pucmm.proyectoqa.application.service.ReportService;
import icc354.pucmm.proyectoqa.application.service.StockService;
import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración de reportes de inventario contra Postgres real (Testcontainers).
 */
@Testcontainers
class ReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private StockService stockService;

    @Autowired
    private ReportService reportService;

    /**
     * Caso de integración #1: Resumen de inventario
     * Verifica que inventorySummary refleja productos creados con totales y valor positivo.
     */
    @Test
    void inventorySummary_reflectsCreatedProducts() {
        productService.create(new ProductRequest(
                "Report Widget", "REP-SUM-01", null, null,
                new BigDecimal("20.00"), 4, 2, true));

        InventorySummaryResponse summary = reportService.inventorySummary();

        assertThat(summary.totalProducts()).isGreaterThanOrEqualTo(1);
        assertThat(summary.activeProducts()).isGreaterThanOrEqualTo(1);
        assertThat(summary.totalUnits()).isGreaterThanOrEqualTo(4);
        assertThat(summary.inventoryValue()).isGreaterThan(BigDecimal.ZERO);
    }

    /**
     * Caso de integración #2: Productos con stock bajo
     * Verifica que lowStock incluye productos cuya cantidad está en o por debajo del mínimo.
     */
    @Test
    void lowStock_includesProductAtOrBelowMin() {
        ProductResponse low = productService.create(new ProductRequest(
                "Low Stock Item", "REP-LOW-01", null, null,
                new BigDecimal("5.00"), 1, 5, true));

        List<ProductResponse> critical = reportService.lowStock(50);

        assertThat(critical).extracting(ProductResponse::id).contains(low.id());
        assertThat(critical.stream().anyMatch(p -> p.id().equals(low.id()) && p.belowMinStock())).isTrue();
    }

    /**
     * Caso de integración #3: Top productos por salidas
     * Verifica que topProducts ordena por volumen de movimientos OUT, con el más vendido primero.
     */
    @Test
    void topProducts_ranksByOutVolume() {
        ProductResponse a = productService.create(new ProductRequest(
                "Popular", "REP-TOP-A", null, null,
                new BigDecimal("10.00"), 50, 2, true));
        ProductResponse b = productService.create(new ProductRequest(
                "Rare", "REP-TOP-B", null, null,
                new BigDecimal("10.00"), 50, 2, true));

        stockService.registerMovement(
                new StockMovementRequest(a.id(), MovementType.OUT, 10, "many"), "test");
        stockService.registerMovement(
                new StockMovementRequest(b.id(), MovementType.OUT, 2, "few"), "test");

        List<TopProductResponse> top = reportService.topProducts(10);

        assertThat(top).isNotEmpty();
        assertThat(top.get(0).productId()).isEqualTo(a.id());
        assertThat(top.get(0).unitsOut()).isGreaterThanOrEqualTo(10);
    }
}
