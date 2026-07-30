package icc354.pucmm.proyectoqa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seguridad JWT para perfiles {@code docker} / {@code staging} / {@code prod}.
 *
 * Bloques:
 * 1. {@link #dockerSecurityFilterChain} — rutas públicas vs autenticadas + Resource Server
 * 2. {@link #jwtAuthenticationConverter} — convierte claims del JWT en authorities
 * 3. {@link #extractAuthorities} — lee roles de realm_access y resource_access[inventory-api]
 *
 * Esos authorities son los que usa {@code @PreAuthorize("hasAuthority('product:view')")}.
 * En {@code prod}, Swagger queda fuera de permitAll si springdoc está deshabilitado.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile({"docker", "staging", "prod"})
public class DockerSecurityConfig {

    /**
     * Cadena de filtros HTTP:
     * - health / prometheus (y Swagger si está habilitado) → permitAll
     * - resto → autenticado con JWT
     */
    @Bean
    SecurityFilterChain dockerSecurityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled) throws Exception {

        // --- Rutas públicas (sin Bearer) ---
        List<String> publicPaths = new ArrayList<>(List.of(
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/prometheus"
        ));
        if (swaggerUiEnabled) {
            publicPaths.addAll(List.of(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/v3/api-docs/**"
            ));
        }

        http
                // JWT stateless (sin cookies de sesión) — CSRF no aplica (java:S4502)
                .csrf(csrf -> csrf.disable()) // NOSONAR
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                // Valida firma/issuer (issuer-uri / jwk-set-uri en application-*.yml)
                // y aplica el converter de roles
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return http.build();
    }

    /**
     * Une el converter de authorities al flujo OAuth2 Resource Server.
     * {@code app.keycloak.client-id} = client del realm cuyas roles importan (inventory-api).
     */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
            @Value("${app.keycloak.client-id}") String clientId) {

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> extractAuthorities(jwt, clientId));
        return converter;
    }

    /**
     * Extrae roles del JWT:
     * - realm_access.roles
     * - resource_access.{clientId}.roles  ← aquí viven product:view, stock:manage, …
     *
     * Sin prefijo ROLE_: coinciden 1:1 con hasAuthority('product:view').
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt, String clientId) {
        Set<String> roles = new LinkedHashSet<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object realmRoles = realmAccess.get("roles");
            if (realmRoles instanceof List<?> list) {
                list.forEach(role -> roles.add(String.valueOf(role)));
            }
        }

        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            Object clientAccess = resourceAccess.get(clientId);
            if (clientAccess instanceof Map<?, ?> clientMap) {
                Object clientRoles = clientMap.get("roles");
                if (clientRoles instanceof List<?> list) {
                    list.forEach(role -> roles.add(String.valueOf(role)));
                }
            }
        }

        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }
}
