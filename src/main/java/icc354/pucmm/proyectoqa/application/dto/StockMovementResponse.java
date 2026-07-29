package icc354.pucmm.proyectoqa.application.dto;

import icc354.pucmm.proyectoqa.domain.enums.MovementType;

import java.time.Instant;

public record StockMovementResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        MovementType movementType,
        Integer quantityBefore,
        Integer quantityAfter,
        Integer quantityDelta,
        String notes,
        String performedBy,
        Instant createdAt
) {
}
