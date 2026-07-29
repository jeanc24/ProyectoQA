package icc354.pucmm.proyectoqa.controller;

import icc354.pucmm.proyectoqa.application.dto.UserResponse;
import icc354.pucmm.proyectoqa.application.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(
        name = "Users",
        description = "Read-only directory of Keycloak realm users. Create/edit/roles → Keycloak Admin Console."
)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(
            summary = "List realm users",
            description = "Returns users from Keycloak (username, email, enabled, client roles). "
                    + "Read-only: assign roles or create users in the Keycloak Admin Console."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Missing user:manage permission"),
            @ApiResponse(responseCode = "503", description = "Keycloak Admin API unavailable")
    })
    public List<UserResponse> list() {
        return userService.listUsers();
    }
}
