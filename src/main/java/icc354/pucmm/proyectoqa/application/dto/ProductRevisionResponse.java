package icc354.pucmm.proyectoqa.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

// DTO para la respuesta de la auditoria de un producto
public record ProductRevisionResponse(
        Long productId,
        Long revision,
        String revisionType,
        Instant timestamp,
        String username,
        String name,
        String sku,
        String description,
        Long categoryId,
        BigDecimal price,
        Integer quantity,
        Integer minStock,
        Boolean active
) {
}