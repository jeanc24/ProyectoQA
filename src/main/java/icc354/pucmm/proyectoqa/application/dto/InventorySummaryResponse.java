package icc354.pucmm.proyectoqa.application.dto;

import java.math.BigDecimal;

public record InventorySummaryResponse(
        long totalProducts,
        long activeProducts,
        long inactiveProducts,
        long lowStockProducts,
        long totalUnits,
        BigDecimal inventoryValue
) {
}
