package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.application.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private UserService userService;

    /**
     * Caso #1: Servicio de Usuarios - Listar usuarios
     * Verifica que obtiene usuarios de Keycloak, resuelve roles del cliente,
     * mapea a UserResponse y ordena alfabéticamente por username.
     */
    @Test
    void listUsers_mapsAndSortsByUsername() {
        when(keycloakAdminClient.resolveClientUuid()).thenReturn("client-uuid");
        when(keycloakAdminClient.listUsers(anyInt())).thenReturn(List.of(
                Map.of(
                        "id", "2",
                        "username", "viewer",
                        "email", "v@example.com",
                        "enabled", true
                ),
                Map.of(
                        "id", "1",
                        "username", "admin",
                        "enabled", true
                )
        ));
        when(keycloakAdminClient.listClientRoles("2", "client-uuid"))
                .thenReturn(List.of("product:view", "stock:view"));
        when(keycloakAdminClient.listClientRoles("1", "client-uuid"))
                .thenReturn(List.of("user:manage"));

        List<UserResponse> users = userService.listUsers();

        assertThat(users).extracting(UserResponse::username).containsExactly("admin", "viewer");
        assertThat(users.getFirst().roles()).containsExactly("user:manage");
        assertThat(users.get(1).email()).isEqualTo("v@example.com");
        assertThat(users.get(1).roles()).containsExactly("product:view", "stock:view");
    }

    /**
     * Caso #2: Servicio de Usuarios - Usuario sin ID
     * Verifica que si el usuario no tiene id, omite la consulta de roles
     * y devuelve roles vacíos con los demás campos mapeados.
     */
    @Test
    void listUsers_skipsRolesWhenUserIdBlank() {
        when(keycloakAdminClient.resolveClientUuid()).thenReturn("client-uuid");
        when(keycloakAdminClient.listUsers(anyInt())).thenReturn(List.of(
                Map.of("username", "orphan", "enabled", false)
        ));

        List<UserResponse> users = userService.listUsers();

        assertThat(users).singleElement().satisfies(u -> {
            assertThat(u.id()).isEmpty();
            assertThat(u.username()).isEqualTo("orphan");
            assertThat(u.enabled()).isFalse();
            assertThat(u.roles()).isEmpty();
        });
    }
}
