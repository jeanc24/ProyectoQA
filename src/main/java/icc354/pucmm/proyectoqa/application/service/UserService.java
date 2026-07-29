package icc354.pucmm.proyectoqa.application.service;

import icc354.pucmm.proyectoqa.application.dto.UserResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private static final int DEFAULT_MAX_USERS = 100;

    private final KeycloakAdminClient keycloakAdminClient;

    public UserService(KeycloakAdminClient keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    public List<UserResponse> listUsers() {
        String clientUuid = keycloakAdminClient.resolveClientUuid();
        return keycloakAdminClient.listUsers(DEFAULT_MAX_USERS).stream()
                .map(user -> toResponse(user, clientUuid))
                .sorted(Comparator.comparing(UserResponse::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private UserResponse toResponse(Map<String, Object> user, String clientUuid) {
        String id = stringOrEmpty(user.get("id"));
        List<String> roles = id.isBlank()
                ? List.of()
                : keycloakAdminClient.listClientRoles(id, clientUuid);
        return new UserResponse(
                id,
                stringOrEmpty(user.get("username")),
                nullableString(user.get("email")),
                nullableString(user.get("firstName")),
                nullableString(user.get("lastName")),
                Boolean.TRUE.equals(user.get("enabled")),
                roles
        );
    }

    private static String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
