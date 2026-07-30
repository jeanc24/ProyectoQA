package icc354.pucmm.proyectoqa.application.controller;

import icc354.pucmm.proyectoqa.application.dto.ProductRequest;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.service.ProductService;
import icc354.pucmm.proyectoqa.controller.GlobalExceptionHandler;
import icc354.pucmm.proyectoqa.controller.ProductController;
import icc354.pucmm.proyectoqa.domain.exception.DuplicateSkuException;
import icc354.pucmm.proyectoqa.domain.exception.ResourceNotFoundException;
import icc354.pucmm.proyectoqa.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private ProductResponse sampleResponse() {
        return new ProductResponse(
                1L, "Laptop", "LAP-001", "Desc",
                1L, "Electronics",
                new BigDecimal("999.99"), 5, 2, true, false,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T10:00:00Z")
        );
    }

    private String validJson() {
        return """
                {
                  "name": "Laptop",
                  "sku": "LAP-001",
                  "description": "Desc",
                  "categoryId": 1,
                  "price": 999.99,
                  "quantity": 5,
                  "minStock": 2,
                  "active": true
                }
                """;
    }

    /**
     * Caso #1: Controller de Producto - Listar productos (200)
     * Verifica que GET /api/v1/products devuelve HTTP 200 con la página de productos.
     */
    @Test
    void list_returns200() throws Exception {
        when(productService.findAll(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sampleResponse()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("LAP-001"));
    }

    /**
     * Caso #2: Controller de Producto - Obtener producto por ID (200)
     * Verifica que GET /api/v1/products/{id} devuelve HTTP 200 con el producto solicitado.
     */
    @Test
    void get_returns200() throws Exception {
        when(productService.findById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    /**
     * Caso #3: Controller de Producto - Crear producto (201)
     * Verifica que POST /api/v1/products con datos válidos devuelve HTTP 201 Created.
     */
    @Test
    void create_returns201() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    /**
     * Caso #4: Controller de Producto - Crear producto inválido (400)
     * Verifica que POST con datos inválidos devuelve HTTP 400 Bad Request con errores de validación.
     */
    @Test
    void create_returns400_whenInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "sku": "",
                                  "price": -1,
                                  "quantity": -1,
                                  "minStock": -1,
                                  "active": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    /**
     * Caso #5: Controller de Producto - Crear producto con SKU duplicado (409)
     * Verifica que POST con SKU duplicado devuelve HTTP 409 Conflict.
     */
    @Test
    void create_returns409_whenDuplicateSku() throws Exception {
        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new DuplicateSkuException("SKU already exists: LAP-001"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    /**
     * Caso #6: Controller de Producto - Actualizar producto (200)
     * Verifica que PUT /api/v1/products/{id} devuelve HTTP 200 con el producto actualizado.
     */
    @Test
    void update_returns200() throws Exception {
        when(productService.update(eq(1L), any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("LAP-001"));
    }

    /**
     * Caso #7: Controller de Producto - Eliminar producto (204)
     * Verifica que DELETE /api/v1/products/{id} devuelve HTTP 204 No Content e invoca al servicio.
     */
    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).delete(1L);
    }

    /**
     * Caso #8: Controller de Producto - Producto no encontrado (404)
     * Verifica que GET /api/v1/products/{id} inexistente devuelve HTTP 404 Not Found.
     */
    @Test
    void get_returns404_whenNotFound() throws Exception {
        when(productService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found: 99"));

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found: 99"));
    }
}