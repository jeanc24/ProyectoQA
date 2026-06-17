package icc354.pucmm.proyectoqa.controller;

import icc354.pucmm.proyectoqa.application.dto.ProductRevisionResponse;
import icc354.pucmm.proyectoqa.application.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
// Controlador de la auditoria de un producto
@RestController
@RequestMapping("/api/v1/audit/products")
@Tag(
        name = "Audit",
        description = "Product revision history powered by Hibernate Envers"
)
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }
    // Funcion para obtener el historial de auditoria de un producto
    @GetMapping("/{id}")
    // PreAuthorize para verificar si el usuario tiene permisos para ver el historial de auditoria
    @PreAuthorize("hasAuthority('product:view')")
    // Operation para documentar la funcion
    @Operation(
            summary = "Get product audit history",
            description = "Returns all Envers revisions for a product, ordered by revision number."
    )
    // ApiResponses para documentar las respuestas de la funcion
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit history retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    // Funcion para obtener el historial de auditoria de un producto
    public List<ProductRevisionResponse> getHistory(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id) {

        return auditService.getProductHistory(id);
    }
}