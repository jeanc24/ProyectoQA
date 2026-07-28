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

@Tag("api")
@WebMvcTest(ReportController.class)
@Import({GlobalExceptionHandler.class, ApiTestSecurityConfig.class})
@ActiveProfiles("api-test")
class ReportApiScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    void summary_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summary_withoutReportView_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory-summary")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isForbidden());
    }

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

    @Test
    void lowStock_withReportView_returns200() throws Exception {
        when(reportService.lowStock(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports/low-stock")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("report:view"))))
                .andExpect(status().isOk());
    }

    @Test
    void recentMovements_withReportView_returns200() throws Exception {
        when(reportService.recentMovements(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports/recent-movements")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("report:view"))))
                .andExpect(status().isOk());
    }

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
