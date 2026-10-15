package com.condohub.gestion.application.port.out;

import java.util.Set;
import java.util.UUID;

public interface UnidadRepository {

    /** Unidades de las que la persona con ese {@code sub} de Keycloak es titular o residente vigente (F1). */
    Set<UUID> idsDeUnidadesDe(String keycloakSub);
}
