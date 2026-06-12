package icc354.pucmm.proyectoqa.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String sku,
        String description,
        Long categoryId,
        @NotNull @DecimalMin(value = "0.00") BigDecimal price,
        @NotNull @Min(0) Integer quantity,
        @NotNull @Min(0) Integer minStock,
        @NotNull Boolean active
) {
}