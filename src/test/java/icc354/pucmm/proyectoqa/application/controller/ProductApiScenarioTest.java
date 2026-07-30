package icc354.pucmm.proyectoqa.application.controller;

import icc354.pucmm.proyectoqa.application.dto.ProductRequest;
import icc354.pucmm.proyectoqa.application.dto.ProductResponse;
import icc354.pucmm.proyectoqa.application.service.ProductService;
import icc354.pucmm.proyectoqa.controller.GlobalExceptionHandler;
import icc354.pucmm.proyectoqa.controller.ProductController;
import icc354.pucmm.proyectoqa.domain.exception.DuplicateSkuException;
import icc354.pucmm.proyectoqa.domain.exception.ResourceNotFoundException;
import icc354.pucmm.proyectoqa.dto.PageResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas API de escenarios del controlador de productos (seguridad, validación y filtros).
 */
@Tag("api")
@WebMvcTest(ProductController.class)
@Import({GlobalExceptionHandler.class, ApiTestSecurityConfig.class})
@ActiveProfiles("api-test")
class ProductApiScenarioTest {

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

    private static PageResponse<ProductResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
    }

    // --- Seguridad ---

    /**
     * Caso API #1: Listar sin autenticación
     * Verifica que GET /api/v1/products sin credenciales devuelve 401 Unauthorized.
     */
    @Test
    void list_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Caso API #2: Listar como viewer
     * Verifica que un usuario con product:view obtiene 200 al listar productos.
     */
    @Test
    void list_asViewer_returns200() throws Exception {
        when(productService.findAll(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sampleResponse()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/products")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isOk());
    }

    /**
     * Caso API #3: Crear sin autenticación
     * Verifica que POST /api/v1/products sin credenciales devuelve 401 Unauthorized.
     */
    @Test
    void create_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Caso API #4: Crear como viewer
     * Verifica que un viewer sin product:manage recibe 403 al intentar crear un producto.
     */
    @Test
    void create_asViewer_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }

    /**
     * Caso API #5: Crear como admin
     * Verifica que un admin con product:manage obtiene 201 Created al crear un producto.
     */
    @Test
    void create_asAdmin_returns201() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/products")
                        .with(user("admin").authorities(
                                new SimpleGrantedAuthority("product:view"),
                                new SimpleGrantedAuthority("product:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated());
    }

    /**
     * Caso API #6: Actualizar como viewer
     * Verifica que un viewer solo con product:view recibe 403 al actualizar un producto.
     */
    @Test
    void update_asViewerOnly_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/products/1")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isForbidden());
    }

    /**
     * Caso API #7: Eliminar como viewer
     * Verifica que un viewer solo con product:view recibe 403 al eliminar un producto.
     */
    @Test
    void delete_asViewerOnly_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/products/1")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isForbidden());
    }

    // --- Validación / errores ---

    /**
     * Caso API #8: Body inválido al crear
     * Verifica que un POST con campos inválidos devuelve 400 y mensaje de validación.
     */
    @Test
    void create_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(user("admin").authorities(
                                new SimpleGrantedAuthority("product:view"),
                                new SimpleGrantedAuthority("product:manage")))
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
     * Caso API #9: Producto no encontrado
     * Verifica que GET de un ID inexistente devuelve 404 con el mensaje de error.
     */
    @Test
    void get_notFound_returns404() throws Exception {
        when(productService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found: 99"));

        mockMvc.perform(get("/api/v1/products/99")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found: 99"));
    }

    /**
     * Caso API #10: SKU duplicado
     * Verifica que crear un producto con SKU existente devuelve 409 Conflict.
     */
    @Test
    void create_duplicateSku_returns409() throws Exception {
        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new DuplicateSkuException("SKU already exists: LAP-001"));

        mockMvc.perform(post("/api/v1/products")
                        .with(user("admin").authorities(
                                new SimpleGrantedAuthority("product:view"),
                                new SimpleGrantedAuthority("product:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // --- Funcional ---

    /**
     * Caso API #11: Paginación en listado
     * Verifica que los parámetros page y size se propagan al servicio al listar productos.
     */
    @Test
    void list_withPagination_passesPageable() throws Exception {
        when(productService.findAll(any(), any(), any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/products").param("page", "1").param("size", "10")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isOk());
        verify(productService).findAll(isNull(), isNull(), isNull(), isNull(), any());
    }

    /**
     * Caso API #12: Filtro por nombre
     * Verifica que el parámetro name se pasa al servicio al filtrar el listado.
     */
    @Test
    void list_withNameFilter_passesFilter() throws Exception {
        when(productService.findAll(any(), any(), any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/products").param("name", "Laptop")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isOk());
        verify(productService).findAll(eq("Laptop"), isNull(), isNull(), isNull(), any());
    }

    /**
     * Caso API #13: Filtro por SKU
     * Verifica que el parámetro sku se pasa al servicio al filtrar el listado.
     */
    @Test
    void list_withSkuFilter_passesFilter() throws Exception {
        when(productService.findAll(any(), any(), any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/products").param("sku", "LAP")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isOk());
        verify(productService).findAll(isNull(), eq("LAP"), isNull(), isNull(), any());
    }
}
