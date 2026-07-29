package icc354.pucmm.proyectoqa.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        Integer quantity,
        Integer minStock,
        Boolean active,
        boolean belowMinStock,
        Instant createdAt,
        Instant updatedAt
) {
}