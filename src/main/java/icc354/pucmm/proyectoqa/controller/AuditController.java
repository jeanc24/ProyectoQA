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
// Controller for the audit of a product
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
    // Function to get the audit history of a product
    @GetMapping("/{id}")
    // PreAuthorize to check if the user has permissions to view the audit history
    @PreAuthorize("hasAuthority('audit:view')")
    // Operation to document the function
    @Operation(
            summary = "Get product audit history",
            description = "Returns all Envers revisions for a product, ordered by revision number."
    )
    // ApiResponses to document the responses of the function
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Missing audit:view permission"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    // Function to get the audit history of a product
    public List<ProductRevisionResponse> getHistory(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id) {

        return auditService.getProductHistory(id);
    }
}