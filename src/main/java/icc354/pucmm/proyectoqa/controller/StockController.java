package icc354.pucmm.proyectoqa.controller;

import icc354.pucmm.proyectoqa.application.dto.StockMovementRequest;
import icc354.pucmm.proyectoqa.application.dto.StockMovementResponse;
import icc354.pucmm.proyectoqa.application.service.StockService;
import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import icc354.pucmm.proyectoqa.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/stock/movements")
@Tag(name = "Stock", description = "Stock movements: entries, exits and adjustments")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('stock:view')")
    @Operation(
            summary = "List stock movements",
            description = "Returns paginated stock movements with optional filters by product, type and date range."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Movements retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Missing stock:view permission")
    })
    public PageResponse<StockMovementResponse> list(
            @Parameter(description = "Filter by product ID") @RequestParam(required = false) Long productId,
            @Parameter(description = "Filter by movement type") @RequestParam(required = false) MovementType movementType,
            @Parameter(description = "From date (ISO-8601)") @RequestParam(required = false) Instant from,
            @Parameter(description = "To date (ISO-8601)") @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return stockService.findAll(productId, movementType, from, to, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('stock:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register stock movement",
            description = """
                    Registers an IN (add units), OUT (remove units) or ADJUSTMENT (set absolute quantity).
                    IN/OUT require quantity >= 1. ADJUSTMENT sets the new stock level.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Movement registered"),
            @ApiResponse(responseCode = "400", description = "Invalid request or insufficient stock"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "403", description = "Missing stock:manage permission")
    })
    public StockMovementResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = StockMovementRequest.class))
            )
            @Valid @RequestBody StockMovementRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return stockService.registerMovement(request, resolveUsername(jwt));
    }

    static String resolveUsername(Jwt jwt) {
        if (jwt == null) {
            return "system";
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        return preferred != null && !preferred.isBlank() ? preferred : jwt.getSubject();
    }
}
