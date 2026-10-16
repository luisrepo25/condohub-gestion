package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import java.util.UUID;

/** Persona de Gestión y la cuenta de Keycloak con la que entra al sistema (null si aún no tiene). */
public record PersonaCuenta(UUID personaId, String nombre, String keycloakSub) {

    /** F1: solo la cuenta enlazada ve la unidad, por eso una persona no puede cambiar de cuenta sin más. */
    public PersonaCuenta enlazarCon(String sub) {
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("El sub de Keycloak es obligatorio");
        }
        if (keycloakSub != null && !keycloakSub.equals(sub)) {
            throw new ReglaNegocioVioladaException("F1", "La persona ya tiene otra cuenta enlazada");
        }
        return new PersonaCuenta(personaId, nombre, sub);
    }
}