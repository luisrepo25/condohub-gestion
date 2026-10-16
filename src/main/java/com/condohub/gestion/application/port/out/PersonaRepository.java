package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.PerfilDeUsuario.PersonaEnlazada;
import java.util.Optional;

public interface PersonaRepository {

    /** Persona enlazada con ese {@code sub} de Keycloak y sus unidades vigentes (F1); vacío si no hay enlace. */
    Optional<PersonaEnlazada> porKeycloakSub(String keycloakSub);
}