package icc354.pucmm.proyectoqa.controller;

import icc354.pucmm.proyectoqa.application.dto.StockMovementResponse;
import icc354.pucmm.proyectoqa.application.service.StockService;
import icc354.pucmm.proyectoqa.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "ProductStock", description = "Product stock history")
public class ProductStockController {

    private final StockService stockService;

    public ProductStockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/{id}/stock/history")
    @PreAuthorize("hasAuthority('stock:view')")
    @Operation(
            summary = "Product stock history",
            description = "Returns paginated stock movements for a single product, newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History retrieved"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "403", description = "Missing stock:view permission")
    })
    public PageResponse<StockMovementResponse> history(
            @Parameter(description = "Product ID", required = true) @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return stockService.findByProductId(id, pageable);
    }
}
