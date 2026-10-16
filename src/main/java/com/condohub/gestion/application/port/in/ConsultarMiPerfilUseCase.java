package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.PerfilDeUsuario;
import com.condohub.gestion.domain.model.Solicitante;

public interface ConsultarMiPerfilUseCase {

    PerfilDeUsuario consultar(Solicitante solicitante);
}