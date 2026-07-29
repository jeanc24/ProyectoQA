package icc354.pucmm.proyectoqa.application.dto;

public record TopProductResponse(
        Long productId,
        String productName,
        String productSku,
        long unitsOut
) {
}
