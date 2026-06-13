package icc354.pucmm.proyectoqa.controller;

import icc354.pucmm.proyectoqa.application.dto.ProductRequest;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.service.ProductService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(
        name = "Products",
        description = "CRUD operations for product management"
)
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(
            summary = "List products",
            description = "Returns a paginated list of products and allows filtering by name, SKU, category, and active status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameters")
    })
    public PageResponse<ProductResponse> list(
            @Parameter(description = "Filter by product name", example = "Laptop")
            @RequestParam(required = false) String name,

            @Parameter(description = "Filter by product SKU", example = "LAP-001")
            @RequestParam(required = false) String sku,

            @Parameter(description = "Filter by category ID", example = "1")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Filter by active status", example = "true")
            @RequestParam(required = false) Boolean active,

            @ParameterObject
            @PageableDefault(size = 20) Pageable pageable) {

        return productService.findAll(name, sku, categoryId, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get product by ID",
            description = "Returns a single product using its unique identifier."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ProductResponse get(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id) {

        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create product",
            description = "Creates a new product. The SKU must be unique."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "409", description = "A product with the same SKU already exists")
    })
    public ProductResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Product data to create",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequest.class))
            )
            @Valid @RequestBody ProductRequest request) {

        return productService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update product",
            description = "Updates an existing product."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "409", description = "Another product already uses the same SKU")
    })
    public ProductResponse update(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated product data",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ProductRequest.class))
            )
            @Valid @RequestBody ProductRequest request) {

        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete product",
            description = "Deletes a product using its unique identifier."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public void delete(
            @Parameter(description = "Product ID", required = true, example = "1")
            @PathVariable Long id) {

        productService.delete(id);
    }
}