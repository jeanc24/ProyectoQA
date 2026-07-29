package icc354.pucmm.proyectoqa.application.dto;

import java.util.List;

public record UserResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        List<String> roles
) {
}
