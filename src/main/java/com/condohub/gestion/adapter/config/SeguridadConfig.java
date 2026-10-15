package com.condohub.gestion.adapter.config;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Seguridad: todo endpoint exige un JWT de Keycloak (firma, expiración e {@code iss} validados).
 * Los roles del realm ({@code realm_access.roles}) se vuelven {@code ROLE_<ROL>} para {@code @PreAuthorize}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(convertidorDeRoles()))
                        .authenticationEntryPoint(noAutenticado()))
                .exceptionHandling(e -> e.authenticationEntryPoint(noAutenticado()).accessDeniedHandler(sinPermiso()));
        return http.build();
    }

    static JwtAuthenticationConverter convertidorDeRoles() {
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(SeguridadConfig::autoridadesDe);
        return convertidor;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> autoridadesDe(Jwt jwt) {
        Object acceso = jwt.getClaim("realm_access");
        if (!(acceso instanceof Map<?, ?> mapa) || !(mapa.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
    }

    private static AuthenticationEntryPoint noAutenticado() {
        return (req, res, ex) -> problema(res, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado");
    }

    private static AccessDeniedHandler sinPermiso() {
        return (req, res, ex) -> problema(res, HttpServletResponse.SC_FORBIDDEN, "Sin permiso");
    }

    /** RFC 7807. */
    private static void problema(HttpServletResponse res, int estado, String titulo) throws java.io.IOException {
        res.setStatus(estado);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + estado + "}");
    }
}