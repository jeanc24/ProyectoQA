package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.application.dto.InventorySummaryResponse;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.dto.StockMovementResponse;
import icc354.pucmm.proyectoqa.application.dto.TopProductResponse;
import icc354.pucmm.proyectoqa.domain.entity.Product;
import icc354.pucmm.proyectoqa.domain.entity.StockMovement;
import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import icc354.pucmm.proyectoqa.domain.repository.ProductRepository;
import icc354.pucmm.proyectoqa.domain.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private ReportService reportService;

    private Product lowStockProduct;

    @BeforeEach
    void setUp() {
        lowStockProduct = new Product();
        lowStockProduct.setId(1L);
        lowStockProduct.setName("Cable");
        lowStockProduct.setSku("CAB-01");
        lowStockProduct.setPrice(new BigDecimal("5.00"));
        lowStockProduct.setQuantity(1);
        lowStockProduct.setMinStock(5);
        lowStockProduct.setActive(true);
    }

    /**
     * Caso #1: Servicio de Reportes - Resumen de inventario
     * Verifica que agrega los conteos del repositorio (totales, activos, inactivos,
     * bajo stock, unidades y valor de inventario) en InventorySummaryResponse.
     */
    @Test
    void inventorySummary_aggregatesRepositoryCounts() {
        when(productRepository.count()).thenReturn(10L);
        when(productRepository.countByActiveTrue()).thenReturn(8L);
        when(productRepository.countByActiveFalse()).thenReturn(2L);
        when(productRepository.countLowStock()).thenReturn(3L);
        when(productRepository.sumTotalUnits()).thenReturn(100L);
        when(productRepository.sumInventoryValue()).thenReturn(new BigDecimal("1500.00"));

        InventorySummaryResponse summary = reportService.inventorySummary();

        assertThat(summary.totalProducts()).isEqualTo(10);
        assertThat(summary.activeProducts()).isEqualTo(8);
        assertThat(summary.inactiveProducts()).isEqualTo(2);
        assertThat(summary.lowStockProducts()).isEqualTo(3);
        assertThat(summary.totalUnits()).isEqualTo(100);
        assertThat(summary.inventoryValue()).isEqualByComparingTo("1500.00");
    }

    /**
     * Caso #2: Servicio de Reportes - Productos con bajo stock
     * Verifica que mapea los productos con stock bajo a ProductResponse
     * e indica correctamente belowMinStock.
     */
    @Test
    void lowStock_mapsProducts() {
        when(productRepository.findLowStock(any(Pageable.class))).thenReturn(List.of(lowStockProduct));

        List<ProductResponse> result = reportService.lowStock(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sku()).isEqualTo("CAB-01");
        assertThat(result.get(0).belowMinStock()).isTrue();
    }

    /**
     * Caso #3: Servicio de Reportes - Movimientos recientes
     * Verifica que mapea el historial de movimientos de stock a StockMovementResponse
     * con tipo, SKU del producto y datos del movimiento.
     */
    @Test
    void recentMovements_mapsHistory() {
        StockMovement movement = new StockMovement();
        movement.setId(9L);
        movement.setProduct(lowStockProduct);
        movement.setMovementType(MovementType.OUT);
        movement.setQuantityBefore(5);
        movement.setQuantityAfter(1);
        movement.setQuantityDelta(-4);
        movement.setPerformedBy("admin");
        movement.setNotes("Sale");

        when(stockMovementRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(movement)));

        List<StockMovementResponse> result = reportService.recentMovements(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).movementType()).isEqualTo(MovementType.OUT);
        assertThat(result.get(0).productSku()).isEqualTo("CAB-01");
    }

    /**
     * Caso #4: Servicio de Reportes - Top productos por salidas
     * Verifica que mapea las filas de agregación del repositorio a TopProductResponse
     * con nombre, SKU y unidades de salida.
     */
    @Test
    void topProducts_mapsAggregationRows() {
        when(stockMovementRepository.findTopProductsByUnitsOut(any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, "Cable", "CAB-01", 42L}));

        List<TopProductResponse> result = reportService.topProducts(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).unitsOut()).isEqualTo(42);
        assertThat(result.get(0).productName()).isEqualTo("Cable");
    }

    /**
     * Caso #5: Servicio de Reportes - Valor nulo en resumen de inventario
     * Verifica que cuando el valor total del inventario es nulo,
     * se devuelve BigDecimal.ZERO en la respuesta.
     */
    @Test
    void inventorySummary_nullValueBecomesZero() {
        when(productRepository.count()).thenReturn(0L);
        when(productRepository.countByActiveTrue()).thenReturn(0L);
        when(productRepository.countByActiveFalse()).thenReturn(0L);
        when(productRepository.countLowStock()).thenReturn(0L);
        when(productRepository.sumTotalUnits()).thenReturn(0L);
        when(productRepository.sumInventoryValue()).thenReturn(null);

        InventorySummaryResponse summary = reportService.inventorySummary();

        assertThat(summary.inventoryValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
