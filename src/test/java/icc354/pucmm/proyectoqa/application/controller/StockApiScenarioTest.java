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

    @Test
    void list_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/stock/movements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutStockView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/stock/movements")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withStockView_returns200() throws Exception {
        when(stockService.findAll(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sampleMovement()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/stock/movements")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("stock:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].movementType").value("IN"));
    }

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

    @Test
    void productHistory_notFound_returns404() throws Exception {
        when(stockService.findByProductId(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Product not found: 99"));

        mockMvc.perform(get("/api/v1/products/99/stock/history")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("stock:view"))))
                .andExpect(status().isNotFound());
    }

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
