package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.PersonaCuenta;
import java.util.Optional;
import java.util.UUID;

public interface PersonaCuentaRepository {

    Optional<PersonaCuenta> porId(UUID personaId);

    /** Id de la persona que ya tiene enlazada esa cuenta, si hay alguna. */
    Optional<UUID> personaIdDeCuenta(String keycloakSub);

    void guardarEnlace(UUID personaId, String keycloakSub);
}