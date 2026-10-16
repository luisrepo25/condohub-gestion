package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.PersonaCuenta;
import java.util.UUID;

public interface EnlazarCuentaUseCase {

    /** Enlaza la persona con la cuenta de Keycloak. Repetirlo con los mismos datos no cambia nada. */
    PersonaCuenta enlazar(UUID personaId, String keycloakSub);
}