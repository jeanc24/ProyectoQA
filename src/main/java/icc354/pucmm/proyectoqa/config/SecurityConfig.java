package icc354.pucmm.proyectoqa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Perfil {@code local}: desarrollo sin Keycloak.
 *
 * Todos los endpoints quedan abiertos (permitAll). No valida JWT.
 * En Docker / staging / prod se usa {@link DockerSecurityConfig} en su lugar
 * ({@code @Profile} mutuamente excluyente).
 */
@Configuration
@EnableWebSecurity
@Profile("local")
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless / no cookie session — CSRF no aplica (java:S4502)
                .csrf(csrf -> csrf.disable()) // NOSONAR
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
