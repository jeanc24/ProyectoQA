package icc354.pucmm.proyectoqa.controller;

import icc354.pucmm.proyectoqa.application.dto.InventorySummaryResponse;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.dto.StockMovementResponse;
import icc354.pucmm.proyectoqa.application.dto.TopProductResponse;
import icc354.pucmm.proyectoqa.application.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Operational inventory reports for the dashboard")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/inventory-summary")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "Inventory summary", description = "Totals, active/inactive counts, low stock and inventory value.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Missing report:view permission")
    })
    public InventorySummaryResponse inventorySummary() {
        return reportService.inventorySummary();
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "Low stock products", description = "Products where quantity <= minStock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List retrieved"),
            @ApiResponse(responseCode = "403", description = "Missing report:view permission")
    })
    public List<ProductResponse> lowStock(
            @Parameter(description = "Max items to return (1-100)", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        return reportService.lowStock(limit);
    }

    @GetMapping("/recent-movements")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(summary = "Recent stock movements", description = "Latest movements across all products.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Movements retrieved"),
            @ApiResponse(responseCode = "403", description = "Missing report:view permission")
    })
    public List<StockMovementResponse> recentMovements(
            @Parameter(description = "Max items to return (1-100)", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        return reportService.recentMovements(limit);
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAuthority('report:view')")
    @Operation(
            summary = "Top products by OUT volume",
            description = "Products ranked by total units sold/leaving inventory (OUT movements)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranking retrieved"),
            @ApiResponse(responseCode = "403", description = "Missing report:view permission")
    })
    public List<TopProductResponse> topProducts(
            @Parameter(description = "Max items to return (1-100)", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.topProducts(limit);
    }
}
