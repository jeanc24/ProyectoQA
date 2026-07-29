package icc354.pucmm.proyectoqa.application.controller;

import icc354.pucmm.proyectoqa.application.dto.UserResponse;
import icc354.pucmm.proyectoqa.application.service.UserService;
import icc354.pucmm.proyectoqa.controller.GlobalExceptionHandler;
import icc354.pucmm.proyectoqa.controller.UserController;
import icc354.pucmm.proyectoqa.domain.exception.KeycloakAdminException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("api")
@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, ApiTestSecurityConfig.class})
@ActiveProfiles("api-test")
class UserApiScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void list_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutUserManage_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("product:view"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withUserManage_returns200() throws Exception {
        when(userService.listUsers()).thenReturn(List.of(
                new UserResponse(
                        "u1",
                        "admin",
                        "admin@example.com",
                        "Admin",
                        "User",
                        true,
                        List.of("product:view", "user:manage")
                )
        ));

        mockMvc.perform(get("/api/v1/users")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("user:manage"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].roles[0]").value("product:view"));
    }

    @Test
    void list_whenKeycloakDown_returns503() throws Exception {
        when(userService.listUsers())
                .thenThrow(new KeycloakAdminException("Failed to obtain Keycloak admin token"));

        mockMvc.perform(get("/api/v1/users")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("user:manage"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Failed to obtain Keycloak admin token"));
    }
}
