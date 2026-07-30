package icc354.pucmm.proyectoqa.application.controller;

import icc354.pucmm.proyectoqa.application.dto.StockMovementResponse;
import icc354.pucmm.proyectoqa.application.service.StockService;
import icc354.pucmm.proyectoqa.controller.GlobalExceptionHandler;
import icc354.pucmm.proyectoqa.controller.ProductStockController;
import icc354.pucmm.proyectoqa.controller.StockController;
import icc354.pucmm.proyectoqa.domain.enums.MovementType;
import icc354.pucmm.proyectoqa.domain.exception.InsufficientStockException;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas API de escenarios de stock: movimientos, permisos y historial por producto.
 */
@Tag("api")
@WebMvcTest({StockController.class, ProductStockController.class})
@Import({GlobalExceptionHandler.class, ApiTestSecurityConfig.class})
@ActiveProfiles("api-test")
class StockApiScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockService stockService;

    private StockMovementResponse sampleMovement() {
        return new StockMovementResponse(
                1L, 10L, "Laptop", "LAP-001",
                MovementType.IN, 5, 10, 5,
                "Restock", "admin",
                Instant.parse("2026-07-07T12:00:00Z")
        );
    }

    private String validInJson() {
        return """
                {
                  "productId": 10,
                  "movementType": "IN",
                  "quantity": 5,
                  "notes": "Restock"
                }
                """;
    }

    /**
     * Caso API #1: Listar movimientos sin autenticación
     * Verifica que GET /api/v1/stock/movements sin credenciales devuelve 401 Unauthorized.
     */
    @Test
    void list_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/stock/movements"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Caso API #2: Listar sin permiso stock:view
     * Verifica que un usuario sin stock:view recibe 403 al listar movimientos.
     */
    @Test
    void list_withoutStockView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/stock/movements")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Caso API #3: Listar con stock:view
     * Verifica que un usuario con stock:view obtiene 200 y el tipo de movimiento en la respuesta.
     */
    @Test
    void list_withStockView_returns200() throws Exception {
        when(stockService.findAll(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sampleMovement()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/stock/movements")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("stock:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movementType").value("IN"));
    }

    /**
     * Caso API #4: Crear movimiento sin stock:manage
     * Verifica que un usuario sin stock:manage recibe 403 al registrar un movimiento.
     */
    @Test
    void create_withoutStockManage_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/stock/movements")
                        .with(user("viewer").authorities(
                                new SimpleGrantedAuthority("product:view"),
                                new SimpleGrantedAuthority("stock:view")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInJson()))
                .andExpect(status().isForbidden());
    }

    /**
     * Caso API #5: Crear movimiento con stock:manage
     * Verifica que un usuario con stock:manage obtiene 201 y la cantidad resultante en la respuesta.
     */
    @Test
    void create_withStockManage_returns201() throws Exception {
        when(stockService.registerMovement(any(), eq("admin")))
                .thenReturn(sampleMovement());

        mockMvc.perform(post("/api/v1/stock/movements")
                        .with(jwt().jwt(j -> j.claim("preferred_username", "admin"))
                                .authorities(new SimpleGrantedAuthority("stock:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityAfter").value(10));
    }

    /**
     * Caso API #6: Stock insuficiente
     * Verifica que un movimiento OUT con cantidad excesiva devuelve 400 Bad Request.
     */
    @Test
    void create_insufficientStock_returns400() throws Exception {
        when(stockService.registerMovement(any(), any()))
                .thenThrow(new InsufficientStockException("Insufficient stock: requested 20, available 5"));

        mockMvc.perform(post("/api/v1/stock/movements")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("stock:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInJson()))
                .andExpect(status().isBadRequest());
    }

    /**
     * Caso API #7: Historial de producto inexistente
     * Verifica que el historial de un producto no encontrado devuelve 404 Not Found.
     */
    @Test
    void productHistory_notFound_returns404() throws Exception {
        when(stockService.findByProductId(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Product not found: 99"));

        mockMvc.perform(get("/api/v1/products/99/stock/history")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("stock:view"))))
                .andExpect(status().isNotFound());
    }

    /**
     * Caso API #8: Historial de producto existente
     * Verifica que el historial de stock de un producto devuelve 200 con el SKU en la respuesta.
     */
    @Test
    void productHistory_withStockView_returns200() throws Exception {
        when(stockService.findByProductId(eq(10L), any()))
                .thenReturn(new PageResponse<>(List.of(sampleMovement()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/products/10/stock/history")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("stock:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productSku").value("LAP-001"));
    }
}
