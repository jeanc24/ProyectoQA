package icc354.pucmm.proyectoqa.application.dto;

import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockMovementRequest(
        @NotNull Long productId,
        @NotNull MovementType movementType,
        @NotNull @Min(0) Integer quantity,
        @Size(max = 500) String notes
) {
}
