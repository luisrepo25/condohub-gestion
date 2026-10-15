package com.condohub.gestion.domain.model;

import java.util.Set;

/** Quien hace la petición: el {@code sub} del token de Keycloak y sus roles. */
public record Solicitante(String sub, Set<Rol> roles) {

    public Solicitante {
        roles = Set.copyOf(roles);
    }

    public boolean tieneRol(Rol rol) {
        return roles.contains(rol);
    }
}
