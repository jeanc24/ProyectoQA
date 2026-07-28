package icc354.pucmm.proyectoqa.application.controller;

import icc354.pucmm.proyectoqa.application.dto.ProductRevisionResponse;
import icc354.pucmm.proyectoqa.application.service.AuditService;
import icc354.pucmm.proyectoqa.controller.AuditController;
import icc354.pucmm.proyectoqa.controller.GlobalExceptionHandler;
import icc354.pucmm.proyectoqa.domain.exception.ResourceNotFoundException;
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
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("api")
@WebMvcTest(AuditController.class)
@Import({GlobalExceptionHandler.class, ApiTestSecurityConfig.class})
@ActiveProfiles("api-test")
class AuditApiScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    private ProductRevisionResponse sampleRevision() {
        return new ProductRevisionResponse(
                1L,
                1L,
                "ADD",
                Instant.parse("2026-06-01T10:00:00Z"),
                "admin",
                "Laptop",
                "LAP-001",
                "Desc",
                1L,
                new BigDecimal("999.99"),
                5,
                2,
                true
        );
    }

    @Test
    void history_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/audit/products/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void history_withProductViewOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/audit/products/1")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void history_withAuditView_returns200() throws Exception {
        when(auditService.getProductHistory(1L)).thenReturn(List.of(sampleRevision()));

        mockMvc.perform(get("/api/v1/audit/products/1")
                        .with(user("auditor").authorities(new SimpleGrantedAuthority("audit:view"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revisionType").value("ADD"))
                .andExpect(jsonPath("$[0].sku").value("LAP-001"));
    }

    @Test
    void history_notFound_returns404() throws Exception {
        when(auditService.getProductHistory(99L))
                .thenThrow(new ResourceNotFoundException("Product not found: 99"));

        mockMvc.perform(get("/api/v1/audit/products/99")
                        .with(user("auditor").authorities(new SimpleGrantedAuthority("audit:view"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found: 99"));
    }
}
