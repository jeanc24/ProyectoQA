package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.application.dto.InventorySummaryResponse;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.dto.StockMovementResponse;
import icc354.pucmm.proyectoqa.application.dto.TopProductResponse;
import icc354.pucmm.proyectoqa.domain.entity.Category;
import icc354.pucmm.proyectoqa.domain.entity.Product;
import icc354.pucmm.proyectoqa.domain.entity.StockMovement;
import icc354.pucmm.proyectoqa.domain.repository.ProductRepository;
import icc354.pucmm.proyectoqa.domain.repository.StockMovementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public ReportService(ProductRepository productRepository,
                         StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    public InventorySummaryResponse inventorySummary() {
        long total = productRepository.count();
        long active = productRepository.countByActiveTrue();
        long inactive = productRepository.countByActiveFalse();
        long lowStock = productRepository.countLowStock();
        long totalUnits = productRepository.sumTotalUnits();
        BigDecimal value = productRepository.sumInventoryValue();
        if (value == null) {
            value = BigDecimal.ZERO;
        }

        return new InventorySummaryResponse(total, active, inactive, lowStock, totalUnits, value);
    }

    public List<ProductResponse> lowStock(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return productRepository.findLowStock(PageRequest.of(0, size)).stream()
                .map(this::toProductResponse)
                .toList();
    }

    public List<StockMovementResponse> recentMovements(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return stockMovementRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size))
                .getContent()
                .stream()
                .map(this::toMovementResponse)
                .toList();
    }

    public List<TopProductResponse> topProducts(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return stockMovementRepository.findTopProductsByUnitsOut(PageRequest.of(0, size)).stream()
                .map(row -> new TopProductResponse(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
    }

    private ProductResponse toProductResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                product.getPrice(),
                product.getQuantity(),
                product.getMinStock(),
                product.getActive(),
                product.isBelowMinStock(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private StockMovementResponse toMovementResponse(StockMovement movement) {
        Product product = movement.getProduct();
        return new StockMovementResponse(
                movement.getId(),
                product.getId(),
                product.getName(),
                product.getSku(),
                movement.getMovementType(),
                movement.getQuantityBefore(),
                movement.getQuantityAfter(),
                movement.getQuantityDelta(),
                movement.getNotes(),
                movement.getPerformedBy(),
                movement.getCreatedAt()
        );
    }
}
