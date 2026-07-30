package icc354.pucmm.proyectoqa.application.controller;

import icc354.pucmm.proyectoqa.application.dto.InventorySummaryResponse;
import icc354.pucmm.proyectoqa.application.dto.TopProductResponse;
import icc354.pucmm.proyectoqa.application.service.ReportService;
import icc354.pucmm.proyectoqa.controller.GlobalExceptionHandler;
import icc354.pucmm.proyectoqa.controller.ReportController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas API de escenarios del controlador de reportes (permisos y respuestas JSON).
 */
@Tag("api")
@WebMvcTest(ReportController.class)
@Import({GlobalExceptionHandler.class, ApiTestSecurityConfig.class})
@ActiveProfiles("api-test")
class ReportApiScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    /**
     * Caso API #1: Resumen sin autenticación
     * Verifica que GET inventory-summary sin credenciales devuelve 401 Unauthorized.
     */
    @Test
    void summary_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory-summary"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Caso API #2: Resumen sin report:view
     * Verifica que un usuario sin report:view recibe 403 al consultar el resumen.
     */
    @Test
    void summary_withoutReportView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory-summary")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Caso API #3: Resumen con report:view
     * Verifica que un usuario con report:view obtiene 200 y los totales del inventario.
     */
    @Test
    void summary_withReportView_returns200() throws Exception {
        when(reportService.inventorySummary()).thenReturn(
                new InventorySummaryResponse(10, 8, 2, 3, 100, new BigDecimal("1500.00")));

        mockMvc.perform(get("/api/v1/reports/inventory-summary")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("report:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(10))
                .andExpect(jsonPath("$.lowStockProducts").value(3));
    }

    /**
     * Caso API #4: Stock bajo
     * Verifica que GET low-stock con report:view devuelve 200 OK.
     */
    @Test
    void lowStock_withReportView_returns200() throws Exception {
        when(reportService.lowStock(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports/low-stock")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("report:view"))))
                .andExpect(status().isOk());
    }

    /**
     * Caso API #5: Movimientos recientes
     * Verifica que GET recent-movements con report:view devuelve 200 OK.
     */
    @Test
    void recentMovements_withReportView_returns200() throws Exception {
        when(reportService.recentMovements(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports/recent-movements")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("report:view"))))
                .andExpect(status().isOk());
    }

    /**
     * Caso API #6: Top productos
     * Verifica que GET top-products con report:view devuelve 200 y unitsOut en la respuesta.
     */
    @Test
    void topProducts_withReportView_returns200() throws Exception {
        when(reportService.topProducts(anyInt())).thenReturn(
                List.of(new TopProductResponse(1L, "Laptop", "LAP-001", 25L)));

        mockMvc.perform(get("/api/v1/reports/top-products")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("report:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitsOut").value(25));
    }
}
