package com.condohub.gestion.domain.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Quién es el solicitante para CondoHub: su {@code sub} y roles del token, y, si el {@code sub} está enlazado a una
 * persona (F1), su id, nombre y las unidades de las que es titular o residente vigente.
 */
public record PerfilDeUsuario(String sub, Set<Rol> roles, PersonaEnlazada persona) {

    /** Persona de Gestión enlazada con la cuenta de Keycloak. */
    public record PersonaEnlazada(UUID personaId, String nombre, List<UnidadDePersona> unidades) {}

    /** Unidad vista desde la persona; {@code titular} distingue al propietario del residente. */
    public record UnidadDePersona(UUID unidadId, String codigo, String bloque, boolean titular) {}
}