package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Traduce la autenticación de Spring Security al {@link Solicitante} del dominio. */
final class SolicitanteMapper {

    private static final String PREFIJO = "ROLE_";

    private SolicitanteMapper() {}

    static Solicitante desde(Authentication autenticacion) {
        Set<Rol> roles = EnumSet.noneOf(Rol.class);
        for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
            String nombre = autoridad.getAuthority();
            if (nombre.startsWith(PREFIJO)) {
                try {
                    roles.add(Rol.valueOf(nombre.substring(PREFIJO.length())));
                } catch (IllegalArgumentException desconocido) {
                    // rol de Keycloak que no es de CondoHub (offline_access, etc.): se ignora
                }
            }
        }
        return new Solicitante(autenticacion.getName(), roles);
    }
}